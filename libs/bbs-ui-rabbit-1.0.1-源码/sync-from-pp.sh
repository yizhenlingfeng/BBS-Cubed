#!/bin/bash
# usage: bash port/rabbit/sync-from-pp.sh
# BBS UI Rabbit is a copy of BBS++'s interface classes (port/pp/src), renamed to routu.bbsuirabbit.
# This re-copies every file listed in from-pp.txt from port/pp/src with the same renames and patches,
# so interface changes made in BBS++ can be carried over. Files only rabbit has (RabbitSettings,
# RabbitMod, client/*, the decompiled client/ui/film/* and the mixins
# L10nRabbitMixin / UIScreenFileDropMixin / UIDataPathListFilmLibraryMixin / UITextboxFilmLibrarySearchMixin)
# are left alone. Check `diff -r` / the build afterwards; new BBS++ settings or classes need adding by hand.
set -e
cd "$(dirname "$0")"
PP=../pp/src
while read rel; do
  [ -z "$rel" ] && continue
  new=$(echo "$rel" | sed 's|^gbeic/bbsplusplus/|routu/bbsuirabbit/|')
  mkdir -p "src/$(dirname "$new")"
  sed -E \
   -e 's/gbeic\.bbsplusplus\.(BBSPPExtraSettings|BBSAddonsSettings)\b/routu.bbsuirabbit.RabbitSettings/g' \
   -e 's/gbeic\.bbsplusplus\.BBSPlusPlusMod\b/routu.bbsuirabbit.RabbitMod/g' \
   -e 's/\b(BBSPPExtraSettings|BBSAddonsSettings)\b/RabbitSettings/g' \
   -e 's/\bBBSPlusPlusMod\b/RabbitMod/g' \
   -e 's/gbeic\.bbsplusplus/routu.bbsuirabbit/g' \
   -e 's/bbspp\$/rabbit$/g' \
   -e 's/"bbspp\./"rabbit./g' \
   -e 's#config/bbsplusplus/#config/bbs_ui_rabbit/#g' \
   -e 's#getSettingsPath\("bbsplusplus/#getSettingsPath("bbs_ui_rabbit/#g' \
   -e 's#resolve\("bbsplusplus"\)#resolve("bbs_ui_rabbit")#g' \
   -e 's#textures/bbspp/#textures/bbs_ui_rabbit/#g' \
   -e 's#getModContainer\("bbsplusplus"\)#getModContainer("bbs_ui_rabbit")#g' \
   -e 's/"\[BBS\+\+\] /"[BBS UI Rabbit] /g' \
   -e 's/"BBS\+\+ (launcher|film)/"BBS UI Rabbit \1/g' \
   -e 's/Gfx.text\(b, "BBS\+\+", bx/Gfx.text(b, "BBS-Rabbit", bx/' \
   -e 's/"bbspp_restart_emitter"/"rabbit_restart_emitter"/' \
   "$PP/$rel" > "src/$new"
done < from-pp.txt
python3 -I - <<'PY'
import re
def patch(p, f):
    s = open(p, encoding='utf-8').read(); t = f(s)
    open(p, 'w', encoding='utf-8').write(t)
R = 'src/routu/bbsuirabbit/'
# FilmHealth: "file:" audio links are a BBS++ feature (LocalAudio); check the path directly
def fh(s):
    s = s.replace('import routu.bbsuirabbit.client.audio.LocalAudio;\n', '')
    return s.replace('else if (LocalAudio.isLocal(audio) && !LocalAudio.exists(audio.substring(LocalAudio.SOURCE.length() + 1)))',
                     'else if (audio.startsWith("file:") && !new java.io.File(audio.substring(5)).isFile())')
patch(R + 'client/ui/home/FilmHealth.java', fh)
# UIFormEditorMixin: only the morphing panel, not BBS++'s shift-click parent picking
def fe(s):
    i = s.find('    @Inject(method={"pickFormFromRenderer"}')
    if i >= 0: s = s[:i].rstrip() + '\n}\n'
    s = s.replace('    @Shadow\n    protected abstract void pickFormBone(Form var1, String var2);\n\n', '')
    s = s.replace('private void openBBSPPFormList(', 'private void rabbit$openFormList(')
    body = s[s.index('@Mixin'):]
    out = []
    for l in s.split('\n'):
        m = re.match(r'import ([\w.]+)\.(\w+);', l) or re.match(r' \*  ([\w.]+)\.(\w+)$', l)
        if m and not re.search(r'\b' + m.group(2) + r'\b', body): continue
        out.append(l)
    return '\n'.join(out)
patch(R + 'mixin/UIFormEditorMixin.java', fe)
# Pack presets: off with their setting
def pk(s):
    a = '        this.rabbit$quickPresets.clear();\n'
    return s.replace(a, a + '\n        if (!routu.bbsuirabbit.RabbitSettings.isOn(routu.bbsuirabbit.RabbitSettings.packPresets))\n        {\n            return;\n        }\n', 1)
patch(R + 'mixin/PackScreenPresetsMixin.java', pk)
# Film library: grid view unless the user picked the list
patch(R + 'client/ui/home/FilmLibraryStore.java', lambda s: s.replace('    public boolean grid;\n', '    public boolean grid = true;\n'))
PY
left=$(grep -rln "LocalAudio\|directParentPicking\|gbeic\." src --include=*.java || true)
[ -n "$left" ] && echo "check by hand: $left"
echo "synced $(grep -c . from-pp.txt) files from port/pp/src"
