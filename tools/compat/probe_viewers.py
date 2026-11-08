#!/usr/bin/env python3
"""Probe for the recipe viewers (EMI, JEI, REI): which have a Fabric build for the game version in gradle.properties,
and what their plugin API looks like there. Run in CI on a commit marked [probe] (this session cannot reach Modrinth).

Usage: probe_viewers.py <work dir>

Prints VIEWER| lines: the version found, the jar's fabric.mod.json, its entrypoint keys, the API classes it holds, and
javap of the classes a plugin needs."""

import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fetch import game_version, newest  # noqa: E402

VIEWERS = {
    "emi": ["dev/emi/emi/api/"],
    "jei": ["mezz/jei/api/"],
    "rei": ["me/shedaniel/rei/api/client/plugins/", "me/shedaniel/rei/api/client/registry/display/",
            "me/shedaniel/rei/api/common/display/", "me/shedaniel/rei/api/common/category/",
            "me/shedaniel/rei/api/client/gui/widgets/Widgets", "me/shedaniel/rei/api/common/entry/EntryStack",
            "me/shedaniel/rei/api/common/util/EntryStacks", "me/shedaniel/rei/api/common/entry/EntryIngredient"],
}

# the classes a plugin needs, printed whole (javap -p)
DETAIL = {
    "emi": ["dev.emi.emi.api.EmiPlugin", "dev.emi.emi.api.EmiEntrypoint", "dev.emi.emi.api.EmiRegistry",
            "dev.emi.emi.api.EmiApi", "dev.emi.emi.api.recipe.EmiRecipe", "dev.emi.emi.api.recipe.BasicEmiRecipe",
            "dev.emi.emi.api.recipe.EmiRecipeCategory", "dev.emi.emi.api.recipe.EmiInfoRecipe",
            "dev.emi.emi.api.recipe.EmiWorldInteractionRecipe", "dev.emi.emi.api.recipe.EmiWorldInteractionRecipe$Builder",
            "dev.emi.emi.api.stack.EmiStack", "dev.emi.emi.api.stack.EmiIngredient", "dev.emi.emi.api.widget.WidgetHolder",
            "dev.emi.emi.api.widget.SlotWidget", "dev.emi.emi.api.widget.TextWidget", "dev.emi.emi.api.render.EmiTexture",
            "dev.emi.emi.api.render.EmiRenderable", "dev.emi.emi.api.widget.Bounds"],
    "jei": ["mezz.jei.api.IModPlugin", "mezz.jei.api.JeiPlugin", "mezz.jei.api.registration.IRecipeCategoryRegistration",
            "mezz.jei.api.registration.IRecipeRegistration", "mezz.jei.api.registration.IRecipeCatalystRegistration",
            "mezz.jei.api.recipe.RecipeType", "mezz.jei.api.recipe.types.IRecipeType", "mezz.jei.api.recipe.category.IRecipeCategory",
            "mezz.jei.api.recipe.category.AbstractRecipeCategory", "mezz.jei.api.gui.builder.IRecipeLayoutBuilder",
            "mezz.jei.api.gui.builder.IRecipeSlotBuilder", "mezz.jei.api.recipe.RecipeIngredientRole",
            "mezz.jei.api.helpers.IGuiHelper", "mezz.jei.api.helpers.IJeiHelpers", "mezz.jei.api.gui.drawable.IDrawable",
            "mezz.jei.api.constants.VanillaTypes", "mezz.jei.api.gui.widgets.IRecipeExtrasBuilder",
            "mezz.jei.api.recipe.IFocusGroup", "mezz.jei.api.gui.ingredient.IRecipeSlotsView"],
    "rei": ["me.shedaniel.rei.api.client.plugins.REIClientPlugin", "me.shedaniel.rei.api.common.display.Display",
            "me.shedaniel.rei.api.common.display.basic.BasicDisplay", "me.shedaniel.rei.api.client.registry.display.DisplayCategory",
            "me.shedaniel.rei.api.client.registry.display.DisplayRegistry",
            "me.shedaniel.rei.api.client.registry.category.CategoryRegistry", "me.shedaniel.rei.api.common.category.CategoryIdentifier",
            "me.shedaniel.rei.api.client.gui.widgets.Widgets", "me.shedaniel.rei.api.common.util.EntryStacks",
            "me.shedaniel.rei.api.common.entry.EntryIngredient", "me.shedaniel.rei.api.client.registry.entry.EntryRegistry"],
}


def main():
    work = sys.argv[1]
    os.makedirs(work, exist_ok=True)
    version = game_version()
    jars = []
    for slug in VIEWERS:
        try:
            v = newest(slug, version)
        except Exception as e:
            print(f"VIEWER|{slug}|error|{e}")
            continue
        if v is None:
            print(f"VIEWER|{slug}|none|no Fabric build for {version}")
            continue
        print(f"VIEWER|{slug}|version|{v['version_number']} ({v.get('version_type')}) deps={[d.get('project_id') for d in v.get('dependencies', [])]}")
        for f in v["files"]:
            print(f"VIEWER|{slug}|file|{f['filename']} primary={f.get('primary')} {f['url']}")
        f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
        path = os.path.join(work, f["filename"])
        req = urllib.request.Request(f["url"], headers={"User-Agent": "extremedimensions-ci/probe"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as w:
            w.write(r.read())
        jars.append((slug, path))
    # nested jars (REI and others ship their API inside)
    cp = []
    for slug, path in jars:
        cp.append(path)
        with zipfile.ZipFile(path) as z:
            for n in z.namelist():
                if n.endswith(".jar"):
                    out = os.path.join(work, slug + "-" + os.path.basename(n))
                    with open(out, "wb") as w:
                        w.write(z.read(n))
                    cp.append(out)
                    print(f"VIEWER|{slug}|nested|{n}")
            if "fabric.mod.json" in z.namelist():
                fmj = json.loads(z.read("fabric.mod.json"))
                print(f"VIEWER|{slug}|entrypoints|{json.dumps(fmj.get('entrypoints', {}))}")
                print(f"VIEWER|{slug}|depends|{json.dumps(fmj.get('depends', {}))}")
    for slug, _ in jars:
        prefixes = VIEWERS[slug]
        names = set()
        for j in cp:
            with zipfile.ZipFile(j) as z:
                for n in z.namelist():
                    if n.endswith(".class") and any(n.startswith(p) for p in prefixes) and "$" not in n:
                        names.add(n[:-6].replace("/", "."))
        for n in sorted(names):
            print(f"VIEWER|{slug}|class|{n}")
        for c in DETAIL[slug]:
            r = subprocess.run(["javap", "-cp", ":".join(cp), "-p", c], capture_output=True, text=True)
            for line in (r.stdout + r.stderr).splitlines():
                print(f"VIEWERAPI|{slug}|{line}")


if __name__ == "__main__":
    main()
