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
    "jei": ["mezz/jei/api/ingredients/"],
    "rei": ["me/shedaniel/math/", "me/shedaniel/rei/plugin/client/Builtin", "me/shedaniel/rei/api/client/gui/widgets/",
            "dev/architectury/fluid/"],
}

# the classes a plugin needs, printed whole (javap -p)
DETAIL = {
    "emi": ["dev.emi.emi.api.EmiPlugin", "dev.emi.emi.api.EmiRegistry", "dev.emi.emi.api.recipe.BasicEmiRecipe"],
    "jei": ["mezz.jei.api.gui.builder.IIngredientAcceptor", "mezz.jei.api.gui.widgets.ITextWidget",
            "mezz.jei.api.gui.placement.IPlaceable", "mezz.jei.api.runtime.IJeiRuntime", "mezz.jei.api.runtime.IRecipesGui",
            "mezz.jei.api.recipe.IFocusFactory", "mezz.jei.api.gui.builder.ITooltipBuilder",
            "mezz.jei.api.ingredients.IIngredientTypeWithSubtypes", "mezz.jei.api.ingredients.IIngredientType"],
    "rei": ["me.shedaniel.rei.api.common.display.DisplaySerializer", "me.shedaniel.rei.api.common.display.DisplaySerializerRegistry",
            "me.shedaniel.rei.api.common.registry.display.DisplayRegistryCommon",
            "me.shedaniel.rei.api.common.registry.display.DisplayConsumer",
            "me.shedaniel.rei.api.common.registry.display.DisplayConsumer$RecipeDisplayConsumer",
            "me.shedaniel.rei.api.client.gui.widgets.Label", "me.shedaniel.rei.api.client.gui.widgets.Slot",
            "me.shedaniel.rei.api.client.gui.widgets.Widget", "me.shedaniel.rei.api.client.gui.widgets.WidgetWithBounds",
            "me.shedaniel.math.Point", "me.shedaniel.math.Rectangle", "me.shedaniel.rei.api.client.gui.Renderer",
            "me.shedaniel.rei.api.common.entry.EntryStack", "me.shedaniel.rei.api.client.registry.display.DisplayCategoryView",
            "me.shedaniel.rei.api.common.util.Identifiable", "me.shedaniel.rei.api.common.plugins.REIPlugin",
            "me.shedaniel.rei.api.common.plugins.REIPluginProvider", "me.shedaniel.rei.plugin.client.BuiltinClientPlugin",
            "me.shedaniel.rei.api.common.display.basic.BasicDisplay$Serializer",
            "me.shedaniel.rei.api.client.registry.category.CategoryRegistry$CategoryConfiguration",
            "me.shedaniel.rei.api.client.view.ViewSearchBuilder", "me.shedaniel.rei.api.client.REIRuntime"],
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
