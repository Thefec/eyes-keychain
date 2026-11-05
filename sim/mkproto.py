import re, sys
src = open(sys.argv[1], encoding='utf-8').read()
lines = src.split('\n')
pat = re.compile(r'^(?!static|class|struct|enum|if|for|while|switch|return|else|case|#)([A-Za-z_][\w:<>]*(?:[\s\*&]+[A-Za-z_][\w:<>]*)*[\s\*&]+)(\w+)\s*\(([^)]*)\)\s*\{')
protos = []
for l in lines:
    m = pat.match(l)
    if m and m.group(2) not in ('if', 'for', 'while', 'switch'):
        protos.append(f"{m.group(1).strip()} {m.group(2)}({m.group(3)});")
out = []
for l in lines:
    out.append(l)
    if l.startswith('struct Cmd'):
        out.append('// --- otomatik prototipler (Arduino gibi) ---')
        out.extend(protos)
open(sys.argv[2], 'w', encoding='utf-8').write('\n'.join(out) + '\nSerialC *_unused_;\n')
print(len(protos), 'prototip')
