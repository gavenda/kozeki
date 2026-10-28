// Plays a scripted session on a rig with Rigby2D's JS runtime and dumps the pose at set frames.
// bun parity.mjs <rigby core dir> <rig.json> <out.expected.json>
import { readFileSync, writeFileSync } from 'node:fs'
const [core, rigPath, outPath] = process.argv.slice(2)
const { readSkeletonJson } = await import(`${core}/interop.js`)
const { Pose } = await import(`${core}/pose.js`)
const { AnimationState } = await import(`${core}/state.js`)
const sk = readSkeletonJson(JSON.parse(readFileSync(rigPath, 'utf8')), 'rig').skeleton
const names = sk.animations.map((a) => a.name)
const base = names.includes('Idle_01') ? 'Idle_01' : names[0]
const blink = names.includes('Eye_Close_01') ? 'Eye_Close_01' : null
const others = names.filter((n) => n !== base && n !== blink)
const ops = [{ frame: 0, op: 'set', track: 0, name: base, loop: true, mix: 0 }]
let f = 5
others.forEach((n, i) => {
  ops.push({ frame: f, op: 'set', track: 1, name: n, loop: i % 3 !== 2, mix: i % 2 ? 0.25 : 0 })
  if (blink && i % 2 === 0) ops.push({ frame: f + 4, op: 'set', track: 2, name: blink, loop: false, mix: 0.1 })
  if (blink && i % 2 === 0) ops.push({ frame: f + 12, op: 'clear', track: 2 })
  f += 15
})
ops.push({ frame: f, op: 'clear', track: 1 })
const frames = f + 10
const dumpEvery = sk.bones.length > 100 ? 5 : 2
const pose = new Pose(sk)
const state = new AnimationState()
const dumps = []
const dt = 1 / 30
for (let frame = 0; frame <= frames; frame++) {
  for (const o of ops) if (o.frame === frame) {
    if (o.op === 'set') state.setAnimation(sk, o.track, o.name, o.loop, o.mix)
    else state.clearTrack(o.track)
  }
  state.update(sk, frame === 0 ? 0 : dt)
  state.apply(sk, pose)
  if (frame % dumpEvery) continue
  const vertices = []
  for (const i of pose.drawOrder) {
    const att = pose.slotAttachment(sk, i)
    if (!att) { vertices.push(null); continue }
    if (att.type === 'region') {
      const m = pose.attachmentWorld(sk, i, att)
      const hw = att.width / 2, hh = att.height / 2
      vertices.push([[-hw, hh], [hw, hh], [hw, -hh], [-hw, -hh]].flatMap((p) => m.apply(p)))
    } else if (['mesh', 'linkedmesh', 'clipping', 'path', 'boundingbox'].includes(att.type)) {
      vertices.push(pose.worldVertices(sk, i, att).slice())
    } else vertices.push(null)
  }
  dumps.push({
    frame,
    world: pose.world.flatMap((m) => [m.a, m.b, m.c, m.d, m.tx, m.ty]),
    color: pose.color.flat(),
    dark: pose.dark.map((d) => (d ? d.slice() : null)),
    attachment: pose.attachment.slice(),
    drawOrder: pose.drawOrder.slice(),
    vertices,
  })
}
writeFileSync(outPath, JSON.stringify({ ops, frames, dumps }))
console.log(`${rigPath}: ${names.length} animations, ${dumps.length} dumps`)
