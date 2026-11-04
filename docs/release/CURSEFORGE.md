# OneKey · Inventory & Action Optimization

**OneKey** is a modern, ultra-lightweight, zero-runtime-dependency inventory and action optimization mod for **Minecraft 26.3 (Java 25)**, featuring native support for both **Fabric** and **NeoForge**.

---

### Key Features

* **One-Key Inventory Sort (Default: `R`)**: Instantly sorts open containers or player inventory by Category → Name → Count.
* **0ms Optimistic UI Sorting**: Zero client latency with instant local sorting raycast and bundle audio cues, smoothly reconciled with the server.
* **Hotbar Absolute Protection**: Bottom 9 hotbar slots (0..8) are **strictly excluded** from all sorting and quick stacking, keeping your tools and weapons completely intact!
* **Slot Locking (`Alt + Left Click`)**: Easily lock precious item slots with an intuitive high-visibility red border highlight. Locked slots are completely immune to sorting, stacking, and stash transfers, persisted per world save.
* **Quick Stack & Quick Stash (Default: `V` & `B`)**: Automatically scans nearby containers within 6 blocks and merges items into existing matching stacks without manually opening every chest!
* **Mouse Gestures**:
  * **Shift + Double Click**: Fast batch transfer of all matching unlocked items to the opposite container.
  * **Mouse Wheel Scrolling**: Scroll up to pull 1 item from opposite container; scroll down to push 1 item to opposite container.
* **Native In-Game Settings GUI (Default: `K`)**: Beautiful native GUI built directly on Minecraft vanilla screens — zero external UI library bloat (No Cloth Config needed)!
* **Mod Conflict Auto-Detection**: Friendly in-game notification when existing inventory mods (IPN, Mouse Tweaks, Item Scroller) are loaded.

---

### Installation & Requirements

* **Minecraft**: 26.3
* **Java**: 25
* **Fabric**: Fabric Loader >= 0.20.1, Fabric API >= 0.165.2+26.3
* **NeoForge**: NeoForge >= 26.3.0.101
* **Singleplayer & Multiplayer**: Fully supported! For multiplayer, installing on both server and client provides authoritative item conservation and best synchronization.
