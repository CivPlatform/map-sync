use crate::render::ColorFn;

use self::{slope::get_slope_color, terrain::get_terrain_color, topo::get_topo_color};

pub mod slope;
pub mod terrain;
pub mod topo;

pub fn get_color_fn(mode: &str) -> Result<ColorFn, String> {
    match mode {
        "biome" => Err("biome coloring is not implemented".to_string()),
        "slope" => Ok(Box::new(get_slope_color)),
        "terrain" => Ok(Box::new(get_terrain_color)),
        "topo" => Ok(Box::new(get_topo_color)),
        _ => Err(format!("Unknown colorize mode {}", mode).to_string()),
    }
}
