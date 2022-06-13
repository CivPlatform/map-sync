use crate::render::{get_column_in_map, ChunkMap};

pub const TRANSPARENT: u32 = 0;
pub const SHADE_DARKBLACK: u32 = 0x88_00_00_00;
pub const SHADE_LITEBLACK: u32 = 0x44_00_00_00;
pub const SHADE_LITEWHITE: u32 = 0x33_ff_ff_ff;

pub fn get_slope_color(map: &ChunkMap, x: i32, z: i32) -> u32 {
    fn get_ground_y(map: &ChunkMap, x: i32, z: i32) -> i16 {
        get_column_in_map(map, x, z)
            .map(|c| c.ground_layer().y)
            .unwrap_or(-999)
    }

    match get_column_in_map(map, x, z) {
        None => TRANSPARENT,
        Some(col) => {
            let y = col.ground_layer().y;
            let mut ditch = false;
            let mut raise = false;

            // 1m away
            let dy_w1 = y - get_ground_y(map, x - 1, z);
            let dy_n1 = y - get_ground_y(map, x, z - 1);
            let dy_nw = y - get_ground_y(map, x - 1, z - 1);
            for dy in [dy_w1, dy_n1, dy_nw] {
                if dy <= -2 {
                    return SHADE_DARKBLACK;
                } else if dy <= -1 {
                    ditch = true;
                    break;
                } else if dy >= 1 {
                    raise = true;
                }
            }

            // 2m away
            let dy_w2 = y - get_ground_y(map, x - 2, z - 1);
            let dy_n2 = y - get_ground_y(map, x - 1, z - 2);
            let dy_nw2 = y - get_ground_y(map, x - 2, z - 2);
            for dy in [dy_w2, dy_n2, dy_nw2] {
                if dy <= -2 {
                    ditch = true;
                    break;
                }
            }

            if ditch {
                SHADE_LITEBLACK
            } else if raise {
                SHADE_LITEWHITE
            } else {
                TRANSPARENT
            }
        }
    }
}
