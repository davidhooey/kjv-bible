#!/usr/bin/env python3
"""Convert eBible.org's KJV VPL file (e.g. eng-kjv2006_vpl.txt) to the app's kjv.tsv.

VPL lines look like:  GEN 1:1 In the beginning God created the heaven and the earth.
Output lines:         1<TAB>1<TAB>1<TAB>In the beginning God created the heaven and the earth.

Usage: python3 vpl_to_tsv.py <input_vpl.txt> <output.tsv>
"""
import re
import sys

CODES = (
    "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SNG "
    "ISA JER LAM EZK DAN HOS JOL AMO OBA JON MIC NAM HAB ZEP HAG ZEC MAL "
    "MAT MRK LUK JHN ACT ROM 1CO 2CO GAL EPH PHP COL 1TH 2TH 1TI 2TI TIT PHM HEB JAS "
    "1PE 2PE 1JN 2JN 3JN JUD REV"
).split()
# eBible's VPL files use older BibleWorks-style codes for some books.
VPL_CODES = (
    "GEN EXO LEV NUM DEU JOS JDG RUT 1SA 2SA 1KI 2KI 1CH 2CH EZR NEH EST JOB PSA PRO ECC SOL "
    "ISA JER LAM EZE DAN HOS JOE AMO OBA JON MIC NAH HAB ZEP HAG ZEC MAL "
    "MAT MAR LUK JOH ACT ROM 1CO 2CO GAL EPH PHI COL 1TH 2TH 1TI 2TI TIT PHM HEB JAM "
    "1PE 2PE 1JO 2JO 3JO JUD REV"
).split()
BOOK_NUM = {c: i + 1 for i, c in enumerate(CODES)}
BOOK_NUM.update({c: i + 1 for i, c in enumerate(VPL_CODES)})
LINE = re.compile(r"^([1-3A-Z]{3})\s+(\d+):(\d+)\s+(.*)$")

# Expected verse total for the 66-book KJV, used as a sanity check.
EXPECTED_VERSES = 31102


def clean(text: str) -> str:
    text = text.replace("¶", "")           # pilcrow paragraph marks
    text = re.sub(r"<[^>]+>", "", text)         # any stray markup
    text = text.replace("\t", " ")
    return re.sub(r"\s+", " ", text).strip()


def main(src: str, dst: str) -> None:
    rows, skipped = [], 0
    with open(src, encoding="utf-8-sig") as f:
        for raw in f:
            m = LINE.match(raw.strip())
            if not m or m.group(1) not in BOOK_NUM:
                skipped += 1
                continue
            text = clean(m.group(4))
            if not text:
                skipped += 1
                continue
            rows.append((BOOK_NUM[m.group(1)], int(m.group(2)), int(m.group(3)), text))

    rows.sort(key=lambda r: (r[0], r[1], r[2]))
    with open(dst, "w", encoding="utf-8", newline="\n") as out:
        for b, c, v, t in rows:
            out.write(f"{b}\t{c}\t{v}\t{t}\n")

    books = {r[0] for r in rows}
    print(f"wrote {len(rows)} verses from {len(books)} books to {dst} (skipped {skipped} lines)")
    if len(books) != 66 or len(rows) != EXPECTED_VERSES:
        print(f"WARNING: expected 66 books and {EXPECTED_VERSES} verses", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
