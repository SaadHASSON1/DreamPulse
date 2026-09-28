"""Draws brand/story-light.svg and brand/story-dark.svg: the DreamPulse story (fall asleep, the countdown
fills, the crescent becomes the sunrise) as a looping SMIL animation, for places without JavaScript such
as the GitHub README. Run: python tools/story_svg.py

Same geometry as the launcher icon: outer radius 170, cut-out radius 150 offset by 70/-60."""

from pathlib import Path

C, R = 200, 100                      # centre and moon radius, in a 400 x 400 box
CUT_R = R * 150 / 170
CUT = (C + R * 70 / 170, C - R * 60 / 170)
AWAY = (C + 2.6 * R, C - 2.6 * R)    # the cut-out slides off: a full disc, the sun
RING_R = 160
CIRC = 2 * 3.14159265 * RING_R
SWEEP = CIRC * 240 / 360             # the ring leaves a gap at the bottom, like the app

DUR = "9s"
# asleep ... countdown fills ... sunrise ... hold ... back to night
KEYS = "0;0.12;0.55;0.7;0.9;1"
SPLINES = "0 0 1 1;0.45 0 0.55 1;0.65 0 0.35 1;0 0 1 1;0.65 0 0.35 1"


def anim(attr, *values):
    """values at the six key times"""
    return (f'<animate attributeName="{attr}" dur="{DUR}" repeatCount="indefinite" calcMode="spline" '
            f'keyTimes="{KEYS}" keySplines="{SPLINES}" values="{";".join(str(v) for v in values)}"/>')


def svg(moon, sun, track):
    rays = []
    for i in range(8):
        rays.append(f'<line x1="{C}" y1="{C - R * 1.16:.1f}" x2="{C}" y2="{C - R * 1.4:.1f}" '
                    f'transform="rotate({i * 45} {C} {C})"/>')
    cx, cy = CUT
    ax, ay = AWAY
    star = (C + R * 0.70, C - R * 0.70)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400" width="240" height="240" role="img" aria-label="DreamPulse: the countdown starts when you fall asleep">
<defs><mask id="cut"><rect width="400" height="400" fill="#fff"/><circle r="{CUT_R:.1f}" cx="{cx:.1f}" cy="{cy:.1f}" fill="#000">{anim("cx", f"{cx:.1f}", f"{cx:.1f}", f"{cx:.1f}", f"{ax:.1f}", f"{ax:.1f}", f"{cx:.1f}")}{anim("cy", f"{cy:.1f}", f"{cy:.1f}", f"{cy:.1f}", f"{ay:.1f}", f"{ay:.1f}", f"{cy:.1f}")}</circle></mask></defs>
<g fill="none" stroke-width="14" stroke-linecap="round" transform="rotate(150 {C} {C})">
<circle r="{RING_R}" cx="{C}" cy="{C}" stroke="{track}" stroke-dasharray="{SWEEP:.1f} {CIRC:.1f}"/>
<circle r="{RING_R}" cx="{C}" cy="{C}" stroke="{moon}" stroke-dasharray="{SWEEP:.1f} {CIRC:.1f}" stroke-dashoffset="{SWEEP:.1f}">{anim("stroke-dashoffset", f"{SWEEP:.1f}", f"{SWEEP:.1f}", 0, 0, 0, f"{SWEEP:.1f}")}{anim("stroke", moon, moon, moon, sun, sun, moon)}</circle>
</g>
<g stroke="{sun}" stroke-width="12" stroke-linecap="round" opacity="0">{"".join(rays)}{anim("opacity", 0, 0, 0, 1, 1, 0)}</g>
<circle r="{R}" cx="{C}" cy="{C}" fill="{moon}" mask="url(#cut)">{anim("fill", moon, moon, moon, sun, sun, moon)}{anim("r", R, R, R, R * 0.78, R * 0.78, R)}</circle>
<circle r="{R * 0.11:.1f}" cx="{star[0]:.1f}" cy="{star[1]:.1f}" fill="{sun}">{anim("opacity", 1, 1, 1, 0, 0, 1)}</circle>
</svg>
'''


here = Path(__file__).resolve().parents[1] / "brand"
here.mkdir(exist_ok=True)
(here / "story-light.svg").write_text(svg("#534AB7", "#E09A2B", "#E4E2F4"), encoding="utf-8")
(here / "story-dark.svg").write_text(svg("#AFA9EC", "#FAC775", "#262A4D"), encoding="utf-8")
print("ok")
