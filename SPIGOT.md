# Spigot Listing Texts

Icon file: assets/icon.png (64x64 PNG)

Below are ready-to-use texts for the Spigot resource page. Replace placeholders in ALL CAPS.

---

## EN — Short Description
DynamicWorldFusion (Nexus Core) distributes world load between multiple server nodes with live monitoring and manual zone control.

## EN — Full Description
DynamicWorldFusion is a multi-node orchestration plugin for Minecraft servers. It assigns zones to nodes, monitors load in real time, and lets you move zones when needed. The built-in UI shows nodes, zones, players, chunk activity, and recent events.

Key features:
- Master/Node architecture with zone ownership
- Real-time observability UI (nodes, zones, players, chunks, events)
- Manual zone reassignment from the UI
- Optional AI prefetch hints for chunk streaming
- Optional bStats metrics
- Optional update notifications via Spigot

Requirements:
- Java 21
- Paper 1.21+ (Spigot API compatible, tested on Paper)

Installation:
1) Put the plugin jar into `plugins/` on the Master and Node servers.
2) Set `nexus.mode` to `master` on Master and to `node` on nodes.
3) Set `nexus.node.id` and `nexus.node.zone` for each node.
4) Configure `nexus.master.host` and `nexus.master.port` on nodes.
5) Start Master first, then nodes.

Commands:
- /nexuschunk <targetNodeId> <world> <chunkX> <chunkZ>

Permissions:
- dynamicworldfusion.update

Configuration:
- `update.resource-id`: Spigot resource ID (for update checks)
- `bstats.plugin-id`: bStats plugin ID

Support:
- GitHub: GITHUB_URL
- Discord: DISCORD_URL (optional)

---

## RU — Краткое описание
DynamicWorldFusion (Nexus Core) распределяет нагрузку мира между несколькими узлами и показывает всё в живом UI.

## RU — Полное описание
DynamicWorldFusion — плагин оркестрации для серверов Minecraft. Он назначает зоны узлам, следит за нагрузкой в реальном времени и позволяет вручную переносить зоны. Встроенный UI показывает узлы, зоны, игроков, активность чанков и события.

Возможности:
- Архитектура Master/Node с владением зонами
- Живой UI: узлы, зоны, игроки, чанки, события
- Ручное переназначение зон из интерфейса
- AI‑подсказки prefetch (опционально)
- Метрики bStats (опционально)
- Уведомления об обновлениях через Spigot (опционально)

Требования:
- Java 21
- Paper 1.21+ (совместимо со Spigot API, тестировалось на Paper)

Установка:
1) Поместите jar в `plugins/` на Master и Node сервера.
2) На Master выставьте `nexus.mode = master`, на Node — `nexus.mode = node`.
3) Задайте `nexus.node.id` и `nexus.node.zone` для каждого узла.
4) Укажите `nexus.master.host` и `nexus.master.port` на узлах.
5) Запускайте Master первым, затем узлы.

Команды:
- /nexuschunk <targetNodeId> <world> <chunkX> <chunkZ>

Права:
- dynamicworldfusion.update

Настройка:
- `update.resource-id`: ID ресурса Spigot (проверка обновлений)
- `bstats.plugin-id`: ID плагина bStats

Поддержка:
- GitHub: GITHUB_URL
- Discord: DISCORD_URL (опционально)
