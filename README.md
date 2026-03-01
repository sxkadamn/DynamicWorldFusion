# DynamicWorldFusion

![build](https://github.com/sxkadamn/DynamicWorldFusion/actions/workflows/build.yml/badge.svg)
![release](https://github.com/sxkadamn/DynamicWorldFusion/actions/workflows/release.yml/badge.svg)
![license](https://img.shields.io/badge/license-MIT-green.svg)

**EN**

DynamicWorldFusion (Nexus Core) is a multi-node orchestration plugin for Minecraft servers. It helps distribute world load between multiple nodes by assigning zones and moving them when needed. The built-in observability UI shows nodes, zones, players, and chunk activity in real time.

## Features
- Master/Node architecture for zone ownership
- Live observability UI (nodes, players, chunks, events)
- Manual zone reassignment from the UI
- Optional AI prefetch hints for chunk streaming
- bStats metrics (optional)
- Spigot update checker with join notifications (optional)

## Requirements
- Java 21
- Paper 1.21+ (Spigot API compatible, tested on Paper)

## Install
1. Build the jar or download a release.
2. Drop the jar into `plugins/` on **Master** and **Node** servers.
3. Configure `config.yml`:
   - Set `nexus.mode` to `master` on the Master server.
   - Set `nexus.mode` to `node` on each node server.
   - Configure `nexus.node.id` and `nexus.node.zone` per node.
   - Point nodes to the master with `nexus.master.host` and `nexus.master.port`.
4. Start Master first, then nodes.

## Observability UI
On the Master server, open:
```
http://<bind-host>:<bind-port>
```
Controls are in the UI, including manual zone reassignment.

## Update Checker
Set `update.resource-id` to your Spigot resource ID. Players with `dynamicworldfusion.update` will see update notices on join.

## bStats
Set `bstats.plugin-id` to your bStats plugin ID to enable metrics.

## Commands
- `/nexuschunk <targetNodeId> <world> <chunkX> <chunkZ>` — send a test chunk frame

## Permissions
- `dynamicworldfusion.update` — receive update notifications

---

**RU**

DynamicWorldFusion (Nexus Core) — плагин для оркестрации нескольких серверов‑узлов. Он распределяет зоны мира между узлами и умеет переносить их при перегрузке. Встроенный UI показывает живую карту нагрузки, игроков и чанки.

## Возможности
- Архитектура Master/Node для владения зонами
- Живой UI: узлы, зоны, игроки, чанки, события
- Ручное переназначение зон из интерфейса
- AI‑подсказки prefetch (опционально)
- Метрики bStats (опционально)
- Проверка обновлений Spigot с уведомлениями (опционально)

## Требования
- Java 21
- Paper 1.21+ (совместимо со Spigot API, тестировалось на Paper)

## Установка
1. Соберите jar или возьмите релиз.
2. Положите jar в `plugins/` на **Master** и **Node** сервера.
3. Настройте `config.yml`:
   - На Master: `nexus.mode = master`
   - На Node: `nexus.mode = node`
   - Задайте `nexus.node.id` и `nexus.node.zone` для каждого узла
   - Укажите адрес Master в `nexus.master.host` и `nexus.master.port`
4. Запускайте Master первым, затем узлы.

## UI наблюдаемости
На Master откройте:
```
http://<bind-host>:<bind-port>
```
Управление зонами доступно прямо в интерфейсе.

## Проверка обновлений
Укажите `update.resource-id` (ID ресурса на Spigot). Игроки с правом `dynamicworldfusion.update` увидят уведомления при входе.

## bStats
Укажите `bstats.plugin-id`, чтобы включить метрики.

## Команды
- `/nexuschunk <targetNodeId> <world> <chunkX> <chunkZ>` — тестовая отправка чанка

## Права
- `dynamicworldfusion.update` — уведомления об обновлениях
