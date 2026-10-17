import hashlib
import re
import sys
import zipfile


def group_of(name):
    if re.fullmatch(r"classes\d*\.dex", name):
        return "dex"
    return name.split("/")[0] if "/" in name else name


def main(path):
    entries = zipfile.ZipFile(path).infolist()
    groups = {}
    for info in entries:
        line = "%s %08x %d %s" % (info.filename, info.CRC, info.file_size, info.date_time)
        groups.setdefault(group_of(info.filename), []).append(line)
    order = hashlib.sha256("\n".join(info.filename for info in entries).encode()).hexdigest()[:10]
    print("entry order %s, %d entries" % (order, len(entries)))
    for group in sorted(groups):
        digest = hashlib.sha256("\n".join(sorted(groups[group])).encode()).hexdigest()[:10]
        print("%s: %d entries, %s" % (group, len(groups[group]), digest))


main(sys.argv[1])
