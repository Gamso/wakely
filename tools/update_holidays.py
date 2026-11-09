"""Copies the Jollyday holiday data used by Wakely into app/src/main/assets/holidays.

Jollyday (https://github.com/focus-shift/jollyday, Apache License 2.0) describes the public holidays of ~240
countries and their regions as XML rules, with translated names in .properties files. Wakely embeds that data
unmodified and computes the dates itself, offline.

Usage:
    git clone --depth 1 https://github.com/focus-shift/jollyday.git /tmp/jollyday
    python tools/update_holidays.py /tmp/jollyday

What is copied:
  * Holidays_<cc>.xml for every ISO 3166-1 country (two letters) whose NATIONAL holidays only use rules that
    Wakely can compute: fixed dates, Easter-based, weekday-based. Countries whose national holidays follow the
    Islamic or Ethiopian calendars are left out (their dates cannot be computed exactly), and so are the stock
    exchange calendars. Regions with such rules are hidden at run time.
  * holiday_descriptions.properties (English) and holiday_descriptions_fr.properties (French).
  * SOURCE.txt: where and when the data was taken from.
"""
import pathlib
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

NS = "{https://focus_shift.de/jollyday/schema/holiday}"
UNSUPPORTED = {"IslamicHoliday", "EthiopianOrthodoxHoliday", "HinduHoliday", "HebrewHoliday"}
LANGUAGES = ["", "_fr"]

repo = pathlib.Path(sys.argv[1]).resolve()
res = repo / "jollyday-core" / "src" / "main" / "resources"
out = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "holidays"
out.mkdir(parents=True, exist_ok=True)
for old in out.glob("*"):
    old.unlink()

copied = []
for f in sorted((res / "holidays").glob("Holidays_*.xml")):
    code = f.stem.split("_", 1)[1]
    if len(code) != 2:  # stock exchanges and other non-country calendars
        continue
    root = ET.parse(f).getroot()
    holidays = root.find(NS + "Holidays")
    kinds = set() if holidays is None else {c.tag.replace(NS, "") for c in holidays}
    if kinds & UNSUPPORTED:
        continue
    if not kinds and not any(s.find(NS + "Holidays") is not None and len(s.find(NS + "Holidays"))
                             for s in root.iter(NS + "SubConfigurations")):
        continue  # no holiday at all (uninhabited territories)
    shutil.copyfile(f, out / f.name)
    copied.append(code)

for lang in LANGUAGES:
    shutil.copyfile(res / "descriptions" / f"holiday_descriptions{lang}.properties",
                    out / f"holiday_descriptions{lang}.properties")

commit = subprocess.run(["git", "-C", str(repo), "log", "-1", "--format=%H %cs"], capture_output=True, text=True).stdout.strip()
(out / "SOURCE.txt").write_text(
    "Holiday definitions and names from Jollyday (Apache License 2.0)\n"
    "https://github.com/focus-shift/jollyday\n"
    f"Taken from commit {commit}\n"
    f"{len(copied)} countries: {' '.join(copied)}\n",
    encoding="utf-8",
    newline="\n",
)
print(f"{len(copied)} countries copied to {out}")
