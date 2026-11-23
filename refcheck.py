#!/usr/bin/env python3
"""Resolve every Minecraft/loader member reference a mod JAR makes against API jars of each target version.
usage: refcheck.py MOD.jar PREFIXES SKIPPKG -- label=jar1,jar2 label=jar1,...
PREFIXES: comma list of owner prefixes to check (e.g. net/minecraft/,com/mojang/,net/neoforged/)
SKIPPKG: comma list of mod packages whose refs are not checked for a label (e.g. client26 on 1.21 indexes) in the
form label_glob:package, or '-' for none."""
import struct, zipfile, sys, fnmatch

def parse_class(data):
    off = 8; n = struct.unpack_from(">H", data, off)[0]; off += 2; cp = [None]*n; i = 1
    while i < n:
        tag = data[off]; off += 1
        if tag == 1:
            ln = struct.unpack_from(">H", data, off)[0]; off += 2; cp[i] = ("utf8", data[off:off+ln].decode("utf-8", "replace")); off += ln
        elif tag in (3, 4): off += 4
        elif tag in (5, 6): off += 8; i += 1
        elif tag == 7: cp[i] = ("class", struct.unpack_from(">H", data, off)[0]); off += 2
        elif tag == 8: off += 2
        elif tag in (9, 10, 11):
            a, b = struct.unpack_from(">HH", data, off); off += 4; cp[i] = ({9: "fieldref", 10: "methodref", 11: "imethodref"}[tag], a, b)
        elif tag == 12:
            a, b = struct.unpack_from(">HH", data, off); off += 4; cp[i] = ("nat", a, b)
        elif tag == 15: off += 3
        elif tag in (16, 19, 20): off += 2
        elif tag in (17, 18): off += 4
        else: raise ValueError(tag)
        i += 1
    utf = lambda x: cp[x][1]
    cls = lambda x: utf(cp[x][1]) if x else None
    _, this_i, super_i = struct.unpack_from(">HHH", data, off); off += 6
    this, sup = cls(this_i), cls(super_i)
    ni = struct.unpack_from(">H", data, off)[0]; off += 2
    ifaces = [cls(struct.unpack_from(">H", data, off + 2*k)[0]) for k in range(ni)]; off += 2*ni
    def members():
        nonlocal off
        cnt = struct.unpack_from(">H", data, off)[0]; off += 2; out = []
        for _ in range(cnt):
            _a, nm, de, ac = struct.unpack_from(">HHHH", data, off); off += 8
            for _ in range(ac):
                _x, ln = struct.unpack_from(">HI", data, off); off += 6 + ln
            out.append((utf(nm), utf(de)))
        return out
    fields, methods = members(), members()
    refs = [(e[0], cls(e[1]), utf(cp[e[2]][1]), utf(cp[e[2]][2])) for e in cp if e and e[0] in ("fieldref", "methodref", "imethodref")]
    classrefs = [utf(e[1]) for e in cp if e and e[0] == "class"]
    return dict(name=this, super=sup, ifaces=ifaces, fields=set(fields), methods=set(methods), refs=refs, classrefs=classrefs)

def index_jar(path, idx):
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/"):
                try:
                    c = parse_class(z.read(n)); idx.setdefault(c["name"], c)
                except Exception:
                    pass
    return idx

JDK_OK = ("java/", "javax/", "jdk/", "sun/")
_OBJ = {"equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "clone", "finalize"}
JDK_MEMBERS = {"java/lang/Object": _OBJ, "java/lang/Record": _OBJ, "java/lang/Enum": _OBJ | {"name", "ordinal", "compareTo", "valueOf", "getDeclaringClass", "describeConstable"}}

def resolve(idx, owner, name, desc, kind):
    if owner.startswith("["): return "ARRAY"
    if owner not in idx: return "NOCLASS"
    seen, stack = set(), [owner]
    while stack:
        c = stack.pop()
        if c is None or c in seen: continue
        if c.startswith(JDK_OK):
            # only the JDK supertypes Minecraft classes actually extend; their public members
            if name in JDK_MEMBERS.get(c, ()) or (c not in JDK_MEMBERS): return c
            continue
        if c not in idx: continue
        seen.add(c)
        if (name, desc) in (idx[c]["methods"] if kind == "method" else idx[c]["fields"]): return c
        stack.append(idx[c]["super"]); stack.extend(idx[c]["ifaces"])
    return None

def main():
    mod = sys.argv[1]; prefixes = tuple(sys.argv[2].split(",")); skips = [] if sys.argv[3] == "-" else [s.split(":") for s in sys.argv[3].split(",")]
    targets = [a.split("=", 1) for a in sys.argv[5:]]
    modidx = index_jar(mod, {})
    total_bad = 0
    for label, jars in targets:
        idx = {}
        for j in jars.split(","): index_jar(j, idx)
        for k, v in modidx.items(): idx.setdefault(k, v)
        skip_pk = [pk for g, pk in skips if fnmatch.fnmatch(label, g)]
        bad = set()
        for c in modidx.values():
            if any(c["name"].startswith(pk) for pk in skip_pk): continue
            for kind, owner, name, desc in c["refs"]:
                if not owner.startswith(prefixes): continue
                r = resolve(idx, owner, name, desc, "field" if kind == "fieldref" else "method")
                if r in (None, "NOCLASS"): bad.add(f"{r or 'MISSING'} {owner}.{name}{desc}   (from {c['name']})")
            for cr in c["classrefs"]:
                if cr.startswith(prefixes) and cr not in idx and not cr.startswith("["): bad.add(f"NOCLASS {cr}   (from {c['name']})")
        total_bad += len(bad)
        print(f"== {label}: {'OK' if not bad else str(len(bad)) + ' unresolved'}")
        for b in sorted(bad): print("   ", b)
    sys.exit(1 if total_bad else 0)

main()
