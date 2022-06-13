use crate::color::blend;
use crate::render::{ChunkMap, ColorFn};

use self::biome::get_biome_color;
use self::height::get_height_color;
use self::slope::get_slope_color;
use self::terrain::get_terrain_color;
use self::topo::get_topo_color;

pub mod biome;
pub mod height;
pub mod slope;
pub mod terrain;
pub mod topo;

pub fn get_color_fn(mode: &str) -> Result<&'static ColorFn, String> {
    match mode {
        "biome" => Ok(&get_biome_color),
        "height" => Ok(&get_height_color),
        "slope" => Ok(&get_slope_color),
        "terrain" => Ok(&get_terrain_color),
        "topo" => Ok(&get_topo_color),
        _ => Err(format!("Unknown colorize mode {}", mode).to_string()),
    }
}

pub fn superimpose<'a>(
    render_bottom: &'static ColorFn,
    render_top: &'static ColorFn,
) -> impl Fn(&ChunkMap, i32, i32) -> u32 {
    |map: &ChunkMap, x: i32, z: i32| -> u32 {
        let bottom = render_bottom(map, x, z);
        let top = render_top(map, x, z);
        blend(bottom, top)
    }
}

pub fn is_water_blockstate(block_id: u16) -> bool {
    block_id >= 34 && block_id <= 49
}
