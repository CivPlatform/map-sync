#[macro_use]
extern crate lazy_static;
extern crate serde;

use byteorder::{BigEndian, ReadBytesExt};

use crate::chunk_tile::ChunkTile;
use crate::coloring::{get_color_fn, superimpose};
use crate::render::{render_img, Bounds, ChunkMap};

pub mod chunk_tile;
pub mod color;
pub mod coloring;
pub mod render;

fn main() {
    emain().unwrap_or_else(|err| println!("{}", err.to_string()));
}

const USAGE: MyErr = MyErr::StrErr("Usage: render <tile x> <tile z> <tiles directory>");

fn emain<'a>() -> Result<(), MyErr<'a>> {
    let mut args = std::env::args();
    args.next(); // skip arg0 (program name)
    let img_x_str = args.next().ok_or(USAGE)?;
    let img_x: i32 = img_x_str.parse().map_err(|e| format!("tile x: {}", e))?;
    let img_z_str = args.next().ok_or(USAGE)?;
    let img_z: i32 = img_z_str.parse().map_err(|e| format!("tile z: {}", e))?;
    let tiles_dir = args.next().ok_or(USAGE)?;

    // TODO speedup by using array. max 17x17=289 long; requires less hashing
    let mut map = ChunkMap::new();
    let mut stdin = std::io::stdin();
    let num_chunks = stdin.read_u32::<BigEndian>()?;
    for _ in 0..num_chunks {
        let chunk = Box::new(ChunkTile::read(&mut stdin)?);
        map.insert(chunk.pos, chunk);
    }

    let bounds = Bounds {
        w: 256 * img_x,
        n: 256 * img_z,
        e: 256 * (img_x + 1),
        s: 256 * (img_z + 1),
    };

    for color_mode in [
        "terrain",
        "topo",
        // "biome",
        "biome_water",
        "slope",
        "landmass",
        "height",
    ] {
        let img_path = format!("{}/{}/z0/{},{}.png", tiles_dir, color_mode, img_x, img_z);
        println!("Rendering {}", img_path);
        render_img(&img_path, &bounds, &map, get_color_fn(color_mode)?)?;
    }

    let img_path_biome_slope =
        format!("{}/{}/z0/{},{}.png", tiles_dir, "biome_slope", img_x, img_z);
    let biome_slope = superimpose(get_color_fn("biome_water")?, get_color_fn("slope")?);
    render_img(&img_path_biome_slope, &bounds, &map, &biome_slope)?;

    println!("Done");

    Ok(())
}

enum MyErr<'a> {
    StrErr(&'a str),
    StringErr(String),
}
impl<'a> std::fmt::Display for MyErr<'a> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::StrErr(msg) => f.write_str(msg),
            Self::StringErr(msg) => f.write_str(msg),
        }
    }
}
impl<'a> From<&'a str> for MyErr<'a> {
    fn from(err: &'a str) -> Self {
        MyErr::StrErr(err)
    }
}
impl<'a> From<String> for MyErr<'a> {
    fn from(err: String) -> Self {
        MyErr::StringErr(err)
    }
}
impl<'a> From<std::io::Error> for MyErr<'a> {
    fn from(err: std::io::Error) -> Self {
        MyErr::StringErr(err.to_string())
    }
}
impl<'a> From<core::num::ParseIntError> for MyErr<'a> {
    fn from(err: core::num::ParseIntError) -> Self {
        MyErr::StringErr(err.to_string())
    }
}
