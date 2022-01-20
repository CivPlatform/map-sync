use crate::chunk_tile::BlockColumn;
use crate::color::{interpolate, BLACK, TRANSPARENT, WHITE};
use crate::render::{get_column_in_map, ChunkMap};

const SKY_COLOR: u32 = 0xff_88_00_88; // #880088 pink
const MTN_COLOR: u32 = 0xff_32_6e_9f; // #9f6e32 brown
const MID_COLOR: u32 = 0xff_00_ff_ff; // #ffff00 yellow
const COAST_COLOR: u32 = 0xff_00_b6_00; // #00b600 dark green
const SEA_COLOR: u32 = 0xff_ff_d9_00; // #00d9ff light blue

const HIGH_LEVEL: i16 = 240;
const MTN_LEVEL: i16 = 150;
const MID_LEVEL: i16 = 100;
const SEA_LEVEL: i16 = 64;

pub fn get_topo_color(map: &ChunkMap, x: i32, z: i32) -> u32 {
    let col = get_column_in_map(map, x, z);
    if col.is_none() {
        return TRANSPARENT;
    }
    let col = col.unwrap();

    if is_water(col) {
        get_sea_color(0)//XXX col.get_ocean_floor_height())
    } else {
        get_land_color(col.ground_layer().y)
    }
}

pub fn get_sea_color(ocean_floor_height: i16) -> u32 {
    if ocean_floor_height < SEA_LEVEL {
        interpolate(BLACK, SEA_COLOR, 0, SEA_LEVEL, ocean_floor_height)
    } else {
        SEA_COLOR
    }
}

pub fn get_land_color(surface_height: i16) -> u32 {
    if surface_height < SEA_LEVEL {
        interpolate(BLACK, COAST_COLOR, 0, SEA_LEVEL, surface_height)
    } else if surface_height < MID_LEVEL {
        interpolate(COAST_COLOR, MID_COLOR, SEA_LEVEL, MID_LEVEL, surface_height)
    } else if surface_height < MTN_LEVEL {
        interpolate(MID_COLOR, MTN_COLOR, MID_LEVEL, MTN_LEVEL, surface_height)
    } else if surface_height < HIGH_LEVEL {
        interpolate(MTN_COLOR, WHITE, MTN_LEVEL, HIGH_LEVEL, surface_height)
    } else {
        interpolate(WHITE, SKY_COLOR, HIGH_LEVEL, 255, surface_height)
    }
}

fn is_water(column: &BlockColumn) -> bool {
    column.layers.iter().any(|b| false)// XXX b.id == water)
}
