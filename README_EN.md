<div align="center">

# Velocity Shop System - Backend

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.10-green?style=for-the-badge&logo=minecraft)
![Velocity](https://img.shields.io/badge/Platform-Velocity-0066CC?style=for-the-badge&logo=velocity&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

[한국어](README.md) | [English](README_EN.md)

</div>

This plugin is the backend component of the **Velocity Shop System**, running on Spigot/Paper/Purpur servers (Lobby, Survival, Creative, etc.).

## Key Features
- **Shop GUI**: Provides a GUI for players to buy and sell items.
- **Dynamic Pricing System**: An economic system where item prices fluctuate based on transaction volume.
- **Database Integration**: Uses MySQL to store prices and transaction history.
- **Cross-Server Synchronization**: Real-time synchronization of price information with other servers via the Velocity proxy. (*Requires separate [Velocity-ShopSync](https://github.com/minseokk7/Velocity-ShopSync) proxy plugin installation*)

## Installation
1. Place the `VelocityShopSystem-Backend-1.0.2.jar` file into the `plugins` folder of each server.
2. Start the server to generate the `config.yml` file.
3. Configure the database connection information in `config.yml`.

## Configuration (config.yml)
```yaml
server-name: "lobby" # Set a unique name for each server (lobby, survival, creative, etc.)
database:
  host: "localhost"
  port: 3306
  database: "minecraft_db"
  username: "user"
  password: "password"
```

## Commands
- `/shop`: Open the shop GUI
- `/shop setprice <item> <buy_price> <sell_price>`: Set item price (Admin)
- `/shop reset <item>`: Reset item price (Admin)
