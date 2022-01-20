pub const TRANSPARENT: u32 = 0;
pub const BLACK: u32 = 0xff_00_00_00;
pub const WHITE: u32 = 0xff_ff_ff_ff;
pub const SHADE_DARKBLACK: u32 = 0x88_00_00_00;
pub const SHADE_LITEBLACK: u32 = 0x44_00_00_00;
pub const SHADE_LITEWHITE: u32 = 0x33_ff_ff_ff;

pub fn interpolate(col_start: u32, col_stop: u32, val_start: i16, val_stop: i16, val: i16) -> u32 {
    let r_st = col_start & 0xff;
    let g_st = col_start >> 8 & 0xff;
    let b_st = col_start >> 16 & 0xff;
    let r_sp = col_stop & 0xff;
    let g_sp = col_stop >> 8 & 0xff;
    let b_sp = col_stop >> 16 & 0xff;
    rgb(
        interpolate_color_component(r_st, r_sp, val_start, val_stop, val),
        interpolate_color_component(g_st, g_sp, val_start, val_stop, val),
        interpolate_color_component(b_st, b_sp, val_start, val_stop, val),
    )
}

fn interpolate_color_component(
    col_start: u32,
    col_stop: u32,
    val_start: i16,
    val_stop: i16,
    val: i16,
) -> u8 {
    let diff_start = val - val_start;
    let diff_stop = val_stop - val;
    let val_diff = val_stop - val_start;
    ((col_start * diff_stop as u32 + col_stop * diff_start as u32) / val_diff as u32) as u8
}

pub fn rgb(r: u8, g: u8, b: u8) -> u32 {
    0xff000000 | ((b as u32) << 16) | ((g as u32) << 8) | (r as u32)
}
