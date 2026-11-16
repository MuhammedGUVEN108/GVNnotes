"""Temporary visual QA; always restores the original Android font setting."""
import os
import pathlib
import subprocess
import time

root=pathlib.Path(__file__).resolve().parent.parent
adb=os.environ.get("GVNNOTES_ADB") or os.path.join(os.environ.get("ANDROID_HOME",""),"platform-tools","adb.exe")
base=[adb,"-s",os.environ["GVNNOTES_SERIAL"]]  # tablet seri numarası ortam değişkeninden
previous=subprocess.check_output(base+["shell","settings","get","system","font_scale"]).decode().strip()
(root/"logs/library-font-scale-before.txt").write_text(previous,encoding="utf-8")
try:
    subprocess.check_call(base+["shell","settings","put","system","font_scale","1.3"])
    subprocess.check_call(base+["shell","am","force-stop","com.gvn.notes.prototype"])
    subprocess.check_output(base+["shell","am","start","-W","-n","com.gvn.notes.prototype/com.gvn.notes.MainActivity"])
    time.sleep(.3)
    (root/"logs/library-font130.png").write_bytes(subprocess.check_output(base+["exec-out","screencap","-p"]))
finally:
    restore=["shell","settings","delete","system","font_scale"] if previous=="null" else ["shell","settings","put","system","font_scale",previous]
    subprocess.check_call(base+restore)
    subprocess.check_call(base+["shell","am","force-stop","com.gvn.notes.prototype"])
    subprocess.check_output(base+["shell","am","start","-W","-n","com.gvn.notes.prototype/com.gvn.notes.MainActivity"])
print("Original font scale restored:",previous)
