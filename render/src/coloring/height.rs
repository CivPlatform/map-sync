use crate::color::{rgb, TRANSPARENT};
use crate::render::{get_column_in_map, ChunkMap};

const MIN_Y: i16 = -64;
const MAX_Y: i16 = 256 + 64;

pub fn get_height_color(map: &ChunkMap, x: i32, z: i32) -> u32 {
	let col = get_column_in_map(map, x, z);
	if col.is_none() {
		return TRANSPARENT;
	}
	let col = col.unwrap();
	let mut h: u32 = (col.ground_layer().y - MIN_Y) as u32 * 256 / (MAX_Y - MIN_Y) as u32;
	if h > 255 {
		h = 255
	}
	let h = h as u8;
	rgb(h, h, h)
}
