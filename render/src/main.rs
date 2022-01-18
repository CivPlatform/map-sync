use chunk_tile::ChunkTile;
use std::io;

pub mod chunk_tile;

fn main() {
    emain().unwrap_or_else(|err| println!("{:?}", err));
}

fn emain() -> Result<(), io::Error> {
    println!("Hello, world!");

    let mut stdin = io::stdin();
    let chunk = Box::new(ChunkTile::read(&mut stdin)?);

    println!("{:?}", chunk);

    Ok(())
}
