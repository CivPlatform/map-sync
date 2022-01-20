use crate::render::{get_column_in_map, ChunkMap};

pub fn get_terrain_color(map: &ChunkMap, x: i32, z: i32) -> u32 {
    use crate::color::TRANSPARENT;

    let col = get_column_in_map(map, x, z);
    if col.is_none() {
        return TRANSPARENT;
    }
    let col = col.unwrap();

    TRANSPARENT
}
