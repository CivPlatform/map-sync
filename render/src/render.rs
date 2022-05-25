use std::collections::HashMap;
use std::fs;
use std::path::Path;

use crate::chunk_tile::{BlockColumn, ChunkTile};

pub struct Bounds {
    pub w: i32,
    pub n: i32,
    /// exclusive
    pub e: i32,
    /// exclusive
    pub s: i32,
}

impl Bounds {
    fn x_size(&self) -> usize {
        (self.e - self.w).try_into().expect("flipped e/w")
    }
    fn z_size(&self) -> usize {
        (self.s - self.n).try_into().expect("flipped s/n")
    }
}

pub type ChunkMap = HashMap<(i32, i32), Box<ChunkTile>>;

// TODO we could optimize by doing less map.get() by rendering chunk-by-chunk and passing north_chunk/west_chunk for slope
pub fn get_column_in_map<'map>(map: &'map ChunkMap, x: i32, z: i32) -> Option<&'map BlockColumn> {
    let cx = x >> 4;
    let cz = z >> 4;
    let col_nr = ((x & 0xf) + 16 * (z & 0xf)) as usize;
    map.get(&(cx, cz)).map(|c| &c.columns[col_nr])
}

/// returns color in format 0xAABBGGRR
pub type ColorFn = Box<dyn Fn(&ChunkMap, i32, i32) -> u32>;

pub fn render_img(
    img_path: &str,
    bounds: &Bounds,
    map: &ChunkMap,
    color_fn: ColorFn,
) -> Result<(), String> {
    let path = Path::new(img_path).parent().unwrap();
    fs::create_dir_all(path).map_err(|err| {
        format!(
            "Creating parent directory for image {:?} failed: {:?}",
            img_path, err
        )
        .to_string()
    })?;

    let mut pixbuf = vec![0_u32; bounds.x_size() * bounds.z_size()];

    let mut i = 0;
    for z in bounds.n..bounds.s {
        for x in bounds.w..bounds.e {
            pixbuf[i] = color_fn(&map, x, z);
            i += 1;
        }
    }

    lodepng::encode32_file(&img_path, &pixbuf, bounds.x_size(), bounds.z_size())
        .map_err(|err| format!("Encoding image {:?} failed: {:?}", img_path, err).to_string())?;

    Ok(())
}
