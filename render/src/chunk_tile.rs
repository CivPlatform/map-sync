use byteorder::{BigEndian, ReadBytesExt};
use std::io::{self, Read};

pub const CHUNK_WIDTH: usize = 16;
pub const CHUNK_HEIGHT: usize = 16;
pub const CHUNK_COLUMNS: usize = CHUNK_WIDTH * CHUNK_HEIGHT;

pub type ChunkPos = (i32, i32);

#[derive(Debug)]
pub struct ChunkTile {
    pub pos: ChunkPos,
    pub data_version: u16,
    pub columns: [BlockColumn; CHUNK_COLUMNS],
}

impl ChunkTile {
    pub fn read(r: &mut dyn Read) -> Result<ChunkTile, io::Error> {
        let x = r.read_i32::<BigEndian>()?;
        let z = r.read_i32::<BigEndian>()?;
        let data_version = r.read_u16::<BigEndian>()?;
        // TODO build array directly, do not use heap
        let mut columns_vec = Vec::with_capacity(CHUNK_COLUMNS);
        for _ in 0..CHUNK_COLUMNS {
            columns_vec.push(BlockColumn::read(r)?);
        }
        let columns = columns_vec.try_into().expect("number of columns");
        Ok(ChunkTile {
            pos: (x, z),
            data_version,
            columns,
        })
    }
}

#[derive(Debug)]
pub struct BlockColumn {
    pub biome: u16,
    pub light: u8,
    /// top to bottom
    pub layers: Vec<BlockInfo>,
}

impl BlockColumn {
    pub fn read(r: &mut dyn Read) -> Result<BlockColumn, io::Error> {
        let biome = r.read_u16::<BigEndian>()?;
        let light = r.read_u8()?;
        let num_layers = r.read_u8()? as usize;
        let mut layers = Vec::with_capacity(num_layers);
        for _ in 0..num_layers {
            layers.push(BlockInfo::read(r)?);
        }
        Ok(BlockColumn {
            biome,
            light,
            layers,
        })
    }

    pub fn ground_layer(&self) -> &BlockInfo {
        self.layers.last().unwrap_or(&BlockInfo { y: -64, id: 0 })
    }
}

#[derive(Debug)]
pub struct BlockInfo {
    pub y: i16,
    pub id: u16,
}

impl BlockInfo {
    pub fn read(r: &mut dyn Read) -> Result<BlockInfo, io::Error> {
        Ok(BlockInfo {
            y: r.read_i16::<BigEndian>()?,
            id: r.read_u16::<BigEndian>()?,
        })
    }
}
