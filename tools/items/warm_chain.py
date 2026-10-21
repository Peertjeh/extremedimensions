# Draws the 2.34 item textures (Held's Chain, Loose Leaf) at 16x16.
# Usage: python3 tools/items/warm_chain.py src/client/resources/assets/extremedimensions/textures/item
from PIL import Image
ART = {
 'helds_chain': ({'k':(18,16,20),'d':(52,50,58),'m':(92,90,100),'l':(148,146,158),'o':(232,128,52),'y':(255,214,128),'r':(150,60,30)}, [
  "................",
  "....kkk..kkk....",
  "...kmlmkkmlmk...",
  "...kdkdkkdkdk...",
  "..kkkkk..kkkkk..",
  ".kmlk......klmk.",
  ".kdmk......kdmk.",
  ".kkkk......kkkk.",
  ".kmlk......kyok.",
  ".kdmk.....kyook.",
  ".kkkkk...kkroyk.",
  "..kmlmk.kkroork.",
  "..kdkdkkkmlkkk..",
  "...kkk.kmlmk....",
  "......kkdkdk....",
  ".......kkkk....."]),
 'loose_leaf': ({'k':(46,34,26),'p':(206,192,162),'s':(170,154,124),'d':(118,102,80),'n':(28,24,28),'w':(232,222,196)}, [
  "................",
  "..kkkkkkkkkk....",
  "..kwpppppppsk...",
  "..kpppnnnpppk...",
  "..kppnpppnppk...",
  "..kpppnnnpppk...",
  "..kppnpppnppk...",
  "..kpppnnnnnpk...",
  "..kpssppppnsk...",
  "..kpppppppppk...",
  "..kpssssspppk...",
  "..kppppppppsk...",
  "..kpssssppsdk...",
  "..kppppppsdk....",
  "..kkdkpkpkk.....",
  "....k.k.k......."]),
}
def build(name, scale=1):
    pal, rows = ART[name]
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, y, len(row))
        for x, c in enumerate(row):
            if c != '.':
                im.putpixel((x, y), pal[c] + (255,))
    return im
if __name__ == '__main__':
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    for n in ART:
        build(n).save(f'{out}/{n}.png')
    print('ok')
