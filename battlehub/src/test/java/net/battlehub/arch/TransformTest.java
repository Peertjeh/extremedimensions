package net.battlehub.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.battlehub.arch.mod.Attachment;

/** Frames turn coordinates, directions, axes and block properties consistently. */
class TransformTest {
	@Test
	void facingPutsLocalFrontWhereAsked() {
		for (Dir out : Dir.values()) {
			Xform f = Xform.facing(out, 10, 5, -3);
			// local +z (the front) points outwards, local +x to the viewer's right
			assertEquals(out, f.dir(Dir.SOUTH), "front of " + out);
			assertEquals(10 + out.dx, f.x(0, 1));
			assertEquals(-3 + out.dz, f.z(0, 1));
			Dir right = out.back().right();
			assertEquals(right, f.dir(Dir.EAST), "viewer's right for " + out);
		}
	}

	@Test
	void fourQuarterTurnsAreTheIdentity() {
		Xform a = new Xform(1, false, 0, 0, 0);
		Xform b = a.in(a).in(a).in(a);
		assertEquals(0, b.turns());
		assertEquals(7, b.x(7, 3));
		assertEquals(3, b.z(7, 3));
	}

	@Test
	void composedFramesMatchStepByStep() {
		Xform child = new Xform(1, true, 3, 2, -4);
		Xform parent = new Xform(3, false, -20, 64, 11);
		Xform both = child.in(parent);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				int cx = child.x(x, z), cz = child.z(x, z);
				assertEquals(parent.x(cx, cz), both.x(x, z));
				assertEquals(parent.z(cx, cz), both.z(x, z));
			}
		}
		for (Dir d : Dir.values()) assertEquals(parent.dir(child.dir(d)), both.dir(d));
		assertEquals(parent.y(child.y(5)), both.y(5));
	}

	@Test
	void cellsAndPropertiesTurnWithTheFrame() {
		Xform f = Xform.facing(Dir.EAST, 0, 0, 0);
		Cell stairs = Cell.stairs(Slot.ROOF, Dir.NORTH, false, Cell.BODY).in(f);
		assertEquals(f.dir(Dir.NORTH), stairs.facing());
		Cell log = Cell.log(Slot.TIMBER, 'x', Cell.BODY).in(f);
		assertEquals('z', log.axis());
		assertEquals("vine[east=true]", Props.turn("vine[south=true]", new Xform(3, false, 0, 0, 0)));
		assertEquals("red_wall_banner[facing=west]", Props.turn("red_wall_banner[facing=south]", new Xform(1, false, 0, 0, 0)));
		assertEquals("player_head[rotation=4]", Props.turn("player_head[rotation=0]", new Xform(1, false, 0, 0, 0)));
	}

	@Test
	void attachmentsAlignAfterTransformation() {
		Attachment door = new Attachment("entrance", Attachment.Kind.ENTRANCE, 2, 0, 1, Dir.SOUTH, 1, 2);
		Xform f = Xform.facing(Dir.WEST, 40, 7, 12);
		Attachment w = door.in(f);
		assertEquals(Dir.WEST, w.facing());
		assertEquals(f.x(2, 1), w.x());
		assertEquals(f.z(2, 1), w.z());
		assertEquals(7, w.y());
	}
}
