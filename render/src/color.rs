pub const TRANSPARENT: u32 = 0;

pub fn blend(col_bottom: u32, col_top: u32) -> u32 {
    let r_bot = col_bottom & 0xff;
    let g_bot = col_bottom >> 8 & 0xff;
    let b_bot = col_bottom >> 16 & 0xff;
    let a_bot = col_bottom >> 24 & 0xff;
    let r_top = col_top & 0xff;
    let g_top = col_top >> 8 & 0xff;
    let b_top = col_top >> 16 & 0xff;
    let ratio_top = col_top >> 24 & 0xff;
    let ratio_bot = 0xff - ratio_top;
    rgba(
        blend_color_component(r_bot, r_top, ratio_top, ratio_bot),
        blend_color_component(g_bot, g_top, ratio_top, ratio_bot),
        blend_color_component(b_bot, b_top, ratio_top, ratio_bot),
        (a_bot * ratio_bot / 0xff) as u8 + ratio_top as u8,
    )
}

fn blend_color_component(col_bot: u32, col_top: u32, ratio_top: u32, ratio_bot: u32) -> u8 {
    (col_bot * ratio_bot / 0xff) as u8 + (col_top * ratio_top / 0xff) as u8
}

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

pub fn rgba(r: u8, g: u8, b: u8, a: u8) -> u32 {
    ((a as u32) << 24) | ((b as u32) << 16) | ((g as u32) << 8) | (r as u32)
}
