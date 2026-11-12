#!/usr/bin/env python3
"""Probe for the recipe viewers (EMI, JEI, REI), and since 3.52 for the tooltip mods (Jade, WTHIT) and the map mods
(JourneyMap, Xaero's Minimap): which have a Fabric build for the game version in gradle.properties, and what their
plugin API looks like there. Run in CI on a commit marked [probe] (this session cannot reach Modrinth).

Usage: probe_viewers.py <work dir>

Prints VIEWER| lines: the version found, the jar's fabric.mod.json, its entrypoint keys, the API classes it holds, and
javap of the classes a plugin needs (VIEWERAPI|, with their constants). For the tooltip and map mods also VIEWERFILE|
lines (a mod's own plugin list, and how its settings name a plugin's toggles) and VIEWERUSE| lines (the classes that
name a class given in USES, to see how a plugin gets hold of it)."""

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
    "jade": ["snownee/jade/api/"],
    "wthit": ["mcp/mobius/waila/api/"],
    "journeymap": ["journeymap/api/", "journeymap/client/api/"],
    "xaeros-minimap": ["xaero/common/minimap/waypoints/", "xaero/hud/minimap/waypoint/", "xaero/common/api/", "xaero/hud/minimap/module/"],
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
    "jade": ["snownee.jade.api.IWailaPlugin", "snownee.jade.api.IWailaClientRegistration", "snownee.jade.api.IWailaCommonRegistration",
             "snownee.jade.api.IBlockComponentProvider", "snownee.jade.api.IEntityComponentProvider", "snownee.jade.api.IServerDataProvider",
             "snownee.jade.api.IComponentProvider", "snownee.jade.api.IJadeProvider", "snownee.jade.api.ITooltip", "snownee.jade.api.BlockAccessor",
             "snownee.jade.api.EntityAccessor", "snownee.jade.api.Accessor", "snownee.jade.api.config.IPluginConfig", "snownee.jade.api.WailaPlugin"],
    "wthit": ["mcp.mobius.waila.api.IWailaPlugin", "mcp.mobius.waila.api.IRegistrar", "mcp.mobius.waila.api.IClientRegistrar",
              "mcp.mobius.waila.api.ICommonRegistrar", "mcp.mobius.waila.api.IBlockComponentProvider", "mcp.mobius.waila.api.IEntityComponentProvider",
              "mcp.mobius.waila.api.ITooltip", "mcp.mobius.waila.api.IBlockAccessor", "mcp.mobius.waila.api.IEntityAccessor",
              "mcp.mobius.waila.api.IDataProvider", "mcp.mobius.waila.api.IPluginConfig",
              # (what the plugin's server data is written into and read back from, and the newer plugin interfaces)
              "mcp.mobius.waila.api.IDataWriter", "mcp.mobius.waila.api.IDataReader", "mcp.mobius.waila.api.IServerAccessor",
              "mcp.mobius.waila.api.IData", "mcp.mobius.waila.api.IWailaCommonPlugin", "mcp.mobius.waila.api.IWailaClientPlugin",
              "mcp.mobius.waila.api.TooltipPosition"],
    "journeymap": ["journeymap.api.v2.client.IClientPlugin", "journeymap.api.v2.common.IJourneyMapPlugin", "journeymap.api.v2.common.JourneyMapPlugin",
                   "journeymap.api.v2.client.IClientAPI", "journeymap.api.v2.common.waypoint.Waypoint",
                   "journeymap.api.v2.common.waypoint.WaypointFactory", "journeymap.api.v2.common.event.ClientEventRegistry",
                   "journeymap.api.v2.client.event.MappingEvent"],
    "xaeros-minimap": ["xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints", "xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager",
                       "xaero.common.minimap.waypoints.Waypoint", "xaero.hud.minimap.waypoint.WaypointColor",
                       "xaero.hud.minimap.waypoint.WaypointPurpose", "xaero.hud.minimap.module.MinimapSession",
                       "xaero.hud.minimap.waypoint.WaypointSession", "xaero.hud.minimap.waypoint.set.WaypointSet",
                       "xaero.hud.minimap.waypoint.WaypointSharingHandler"],
}

# files printed whole from a mod's jars (its own plugin list)
FILES = {
    "wthit": ["waila_plugins.json", "wthit_plugins.json"],
}
# a mod's English lang file, and the start of the keys of it printed (how its settings name a plugin's toggles)
LANG = {
    "jade": ("assets/jade/lang/en_us.json", "config.jade.plugin_minecraft."),
    "wthit": ("assets/waila/lang/en_us.json", "config.waila.plugin_minecraft."),
}
# the classes of a mod whose bytes name this, so the way a plugin reaches it shows
USES = {
    "xaeros-minimap": "ThirdPartyWaypoint",
    "journeymap": "WaypointFactory",
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
            r = subprocess.run(["javap", "-cp", ":".join(cp), "-p", "-constants", c], capture_output=True, text=True)
            for line in (r.stdout + r.stderr).splitlines():
                print(f"VIEWERAPI|{slug}|{line}")
        # (the mod's own jar and the jars nested in it)
        mine = [path for s2, path in jars if s2 == slug] + [j for j in cp if os.path.basename(j).startswith(slug + "-")]
        for j in mine:
            with zipfile.ZipFile(j) as z:
                names = z.namelist()
                for f in FILES.get(slug, []):
                    if f in names:
                        for line in z.read(f).decode("utf-8", "replace").splitlines():
                            print(f"VIEWERFILE|{slug}|{f}|{line}")
                if slug in LANG and LANG[slug][0] in names:
                    keys = [k for k in json.loads(z.read(LANG[slug][0])) if k.startswith(LANG[slug][1])]
                    for k in keys[:8]:
                        print(f"VIEWERFILE|{slug}|lang|{k}")
                if slug in USES:
                    for n in names:
                        if n.endswith(".class") and USES[slug].encode() in z.read(n):
                            print(f"VIEWERUSE|{slug}|{n[:-6].replace('/', '.')}")


if __name__ == "__main__":
    main()
