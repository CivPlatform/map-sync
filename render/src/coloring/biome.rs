use crate::color::rgb;
use crate::render::{get_column_in_map, ChunkMap};
use std::fs::File;
use std::io::BufReader;

pub fn get_biome_color(map: &ChunkMap, x: i32, z: i32) -> u32 {
	match get_column_in_map(map, x, z) {
		None => 0,
		Some(col) => BIOME_COLORS[col.biome as usize],
	}
}

lazy_static! {
	pub static ref BIOME_COLORS: Vec<u32> = {
		#[derive(serde::Deserialize)]
		struct BiomeJson {
			id: usize,
			color: u32,
			temperature: f32,
			rainfall: f32,
		}
		let file = File::open("biomes.json").expect("reading biomes.json");
		let reader = BufReader::new(file);
		let json: Vec<BiomeJson> = serde_json::from_reader(reader).expect("parsing biomes.json");
		let last_biome = json.last().expect("getting last biomes.json entry");
		let mut colors = vec![0_u32; last_biome.id + 1];
		for o in json {
			// TODO from temperature and rainfall
			let b = (o.color & 0xff) as u8;
			let g = ((o.color >> 8) & 0xff) as u8;
			let r = ((o.color >> 16) & 0xff) as u8;
			colors[o.id] = rgb(r, g, b);
		}
		colors
	};
}
