# usage: python3 -I port/rabbit/guide/make_guide.py <out.html>
# Builds the single-file guide: base.css goes into {{CSS}}, every src="img:NAME" becomes a data URI
# of img/NAME.* (screenshots re-encoded as 1600 px WebP to keep the page small).
import base64, io, os, re, sys
from PIL import Image

here = os.path.dirname(os.path.abspath(__file__))
html = open(os.path.join(here, 'guide.html'), encoding='utf-8').read()
html = html.replace('{{CSS}}', open(os.path.join(here, 'base.css'), encoding='utf-8').read())

cache = {}

def uri(name):
    if name in cache:
        return cache[name]
    path = next(os.path.join(here, 'img', f) for f in os.listdir(os.path.join(here, 'img')) if os.path.splitext(f)[0] == name)
    if path.endswith('.webp'):
        im = Image.open(path).convert('RGB')
        if im.width > 1600:
            im = im.resize((1600, round(im.height * 1600 / im.width)), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, 'WEBP', quality=84, method=6)
        data, mime = buf.getvalue(), 'image/webp'
    else:
        data, mime = open(path, 'rb').read(), 'image/png'
    cache[name] = 'data:%s;base64,%s' % (mime, base64.b64encode(data).decode())
    return cache[name]

html = re.sub(r'src="img:(\w+)"', lambda m: 'src="%s"' % uri(m.group(1)), html)
open(sys.argv[1], 'w', encoding='utf-8').write(html)
print('%s  %.0f KB' % (sys.argv[1], os.path.getsize(sys.argv[1]) / 1024))
