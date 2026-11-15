#!/usr/bin/env python3
from pathlib import Path
import argparse
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont

W, H = 1080, 1920
GOLD = (224,185,108,255)
PALE = (255,232,178,255)
DARK = (10,17,27,210)
STROKE = (217,176,107,224)

def verify(path):
    cmap = set()
    font = TTFont(path)
    for table in font["cmap"].tables:
        cmap.update(table.cmap.keys())
    needed = [0x05D0, 0x05D1, 0x05EA, 0x0030, 0x0039, 0x003A, 0x0022]
    missing = [hex(x) for x in needed if x not in cmap]
    if missing:
        raise RuntimeError(f"{path.name} lacks Hebrew/digit glyphs: {missing}")

def ft(d, name, size):
    return ImageFont.truetype(str(d/name), size, layout_engine=ImageFont.Layout.RAQM)

def rtl(draw, xy, text, font, fill):
    draw.text(xy, text, font=font, fill=fill, anchor="mm", direction="rtl", language="he")

def panel(bg, fonts, pair, label, out):
    title_name, num_name, body_name, caption = pair
    scale = min(W/bg.width, H/bg.height)
    resized = bg.resize((round(bg.width*scale), round(bg.height*scale)), Image.Resampling.LANCZOS)
    im = Image.new("RGBA", (W,H), (3,8,16,255))
    im.alpha_composite(resized, ((W-resized.width)//2, (H-resized.height)//2))
    im.alpha_composite(Image.new("RGBA", (W,H), (4,10,20,34)))
    d = ImageDraw.Draw(im)
    title, nums, body = ft(fonts,title_name,88), ft(fonts,num_name,112), ft(fonts,body_name,44)
    date, quote = ft(fonts,body_name,49), ft(fonts,body_name,44)
    # Match the original physical top-left settings control; label the option at top-right.
    d.rounded_rectangle((42,47,184,189), radius=72, fill=(11,20,34,214), outline=(255,255,255,184), width=3)
    gear_path = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    gear_font = ImageFont.truetype(str(gear_path), 76) if gear_path.exists() else ft(fonts,body_name,64)
    d.text((113,116), "⚙", font=gear_font, fill=(255,255,255,255), anchor="mm")
    d.rounded_rectangle((W-250,42,W-40,112), radius=24, fill=(7,15,28,190), outline=(224,185,108,210), width=2)
    rtl(d,(W-145,77),label,ft(fonts,body_name,30),PALE)
    rtl(d,(540,393),"הרגע הגדול מתקרב",title,GOLD)
    d.rounded_rectangle((292,527,788,926), radius=42, fill=DARK, outline=STROKE, width=4)
    rtl(d,(540,692),"45",nums,PALE)
    rtl(d,(540,815),"ימים",body,(241,216,164,255))
    for i,(value,word) in enumerate([("08","שעות"),("42","דקות"),("19","שניות")]):
        x1=51+i*328; x2=x1+306
        d.rounded_rectangle((x1,974,x2,1200), radius=40, fill=DARK, outline=STROKE, width=4)
        rtl(d,((x1+x2)//2,1052),value,nums,PALE)
        rtl(d,((x1+x2)//2,1138),word,body,(241,216,164,255))
    rtl(d,(540,1273),"ט\"ו בחשוון תשפ\"ז",date,PALE)
    rtl(d,(540,1360),"כל יום הוא הזדמנות חדשה להתקדם",quote,(255,255,255,255))
    im.convert("RGB").save(out, quality=95)
    return im.convert("RGB")

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--background",type=Path,required=True)
    ap.add_argument("--font-dir",type=Path,required=True)
    ap.add_argument("--output-dir",type=Path,required=True)
    a=ap.parse_args()
    a.output_dir.mkdir(parents=True,exist_ok=True)
    bg=Image.open(a.background).convert("RGBA")
    families=["SuezOne-Regular.ttf","FrankRuhlLibre-500.ttf","FrankRuhlLibre-700.ttf","FrankRuhlLibre-900.ttf","DavidLibre-Regular.ttf","DavidLibre-Medium.ttf","Bellefair-Regular.ttf"]
    for name in families: verify(a.font_dir/name)
    pairs=[
        ("SuezOne-Regular.ttf","SuezOne-Regular.ttf","FrankRuhlLibre-500.ttf","כותרת וספרות: Suez One 400 | טקסט: Frank Ruhl Libre Medium 500"),
        ("FrankRuhlLibre-900.ttf","FrankRuhlLibre-900.ttf","DavidLibre-Medium.ttf","כותרת וספרות: Frank Ruhl Libre Black 900 | טקסט: David Libre Medium 500"),
        ("Bellefair-Regular.ttf","FrankRuhlLibre-700.ttf","DavidLibre-Regular.ttf","כותרת: Bellefair 400 | ספרות: Frank Ruhl Libre Bold 700 | טקסט: David Libre 400")
    ]
    labels=["אפשרות א׳","אפשרות ב׳","אפשרות ג׳"]
    panels=[]
    for i,(pair,label) in enumerate(zip(pairs,labels),1):
        panels.append(panel(bg,a.font_dir,pair,label,a.output_dir/f"font-pair-{i}.png"))
    combined=Image.new("RGB",(W*3,H),(8,12,22))
    for i,p in enumerate(panels): combined.paste(p,(i*W,0))
    combined.save(a.output_dir/"font-pairs-1080x1920-side-by-side.png",quality=95)
    (a.output_dir/"font-pairs-details.txt").write_text(
        "שלוש הדמיות המבוססות על TTF אמיתיים; כיסוי עברי אומת בקובצי הגופנים.\n"
        "א: Suez One Regular 400 + Frank Ruhl Libre Medium 500.\n"
        "ב: Frank Ruhl Libre Black 900 + David Libre Medium 500.\n"
        "ג: Bellefair Regular 400 + Frank Ruhl Libre Bold 700 + David Libre Regular 400.\n"
        "כל המשפחות ברישיון SIL Open Font License 1.1. משקלי Frank Ruhl Libre הופקו מקובץ variable למשקלים סטטיים באמצעות fontTools.\n"
        "הגופנים נשמרים בתיקיית העבודה הזמנית בלבד ולא הוטמעו באפליקציה.\n",encoding="utf-8")

if __name__=="__main__": main()
