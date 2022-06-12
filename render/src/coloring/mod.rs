use crate::render::ColorFn;

use self::height::get_height_color;
use self::slope::get_slope_color;
use self::terrain::get_terrain_color;
use self::topo::get_topo_color;

pub mod height;
pub mod slope;
pub mod terrain;
pub mod topo;

pub fn get_color_fn(mode: &str) -> Result<ColorFn, String> {
    match mode {
        "biome" => Err("biome coloring is not implemented".to_string()),
        "height" => Ok(Box::new(get_height_color)),
        "slope" => Ok(Box::new(get_slope_color)),
        "terrain" => Ok(Box::new(get_terrain_color)),
        "topo" => Ok(Box::new(get_topo_color)),
        _ => Err(format!("Unknown colorize mode {}", mode).to_string()),
    }
}
