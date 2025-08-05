# Ghost Detector

A Minecraft Fabric mod that detects and reports ghost totem events, kit loading, and provides armor durability warnings for Crystal PvP servers.

## Features

### 🚨 Ghost Totem Detection
- **Automatic Detection**: Detects ghost totems in both main hand and off-hand
- **Multiple Death Types**: Works with vanilla death, spectator mode transitions, and inventory clear deaths
- **Precise Timing**: Measures totem hold duration in milliseconds and game ticks
- **Smart Targeting**: Automatically detects nearby players and sends private messages when possible

### 🎯 Dual-Mode System
- **Clipboard Mode (Default)**: Copies detection command to clipboard and shows big unmissable message
- **Macro Mode**: Sends detection command directly to chat (check your server's rules!)
- **Easy Switching**: Toggle between modes with simple commands

### ⚡ Kit Detection
- **Visual Effects**: 4 simultaneous lightning strikes on the player when kit is detected
- **Audio Feedback**: Single thunder sound for each kit detection
- **Clean Design**: No particle effects or other visual clutter

### 🛡️ Armor Durability Checker
- **Queue Protection**: Warns before joining queue with damaged armor
- **Smart Confirmation**: Allows re-queuing with confirmation if armor is damaged
- **95% Threshold**: Warns if any armor piece is below 95% durability

### 🔴 Totem Warning Overlay
- **Visual Alert**: Red overlay appears when not holding a totem
- **Safety Reminder**: Helps prevent accidental deaths without totem protection

## Commands

### Ghost Detection Commands
- `/gd` - Show help for all ghost detector commands
- `/gd macro` - Toggle chat macro mode (sends commands directly to chat)
- `/gd clipboard` - Toggle clipboard mode (copies commands to clipboard)

### Queue Commands
- `/q` - Join queue (automatically checks armor durability first)
- `/rtpqueue` - Alternative queue command (also checks durability)
- `/queue` - Alternative queue command (also checks durability)

## How It Works

### Ghost Totem Detection
1. **Detection Methods**:
   - Health drop to 0
   - Transition to spectator mode
   - Inventory clear while holding totem
   - Chat death messages

2. **Message Format**:
   - `<Mainhand/Offhand Ghost Detected> totem held for Xms (Y ticks)`
   - Automatically targets nearby players with private messages when possible

3. **Clipboard Mode**:
   - Shows big red bordered message in chat
   - Copies detection command to clipboard
   - Displays reminder about macro mode after 3 seconds

4. **Macro Mode**:
   - Sends detection command directly to chat
   - Immediate action (use with caution on strict servers)

### Kit Detection
- Monitors chat for kit loading messages
- Triggers 4 lightning strikes simultaneously on the player
- Plays thunder sound effect
- No particle effects or additional visual clutter

### Armor Durability
- Checks all armor pieces before queue commands
- Warns if any piece is below 95% durability
- Allows confirmation to queue with damaged armor
- Resets confirmation after 30 seconds

## Installation

1. Install Fabric loader for Minecraft 1.21.3
2. Place the `ghost-detector.jar` file in your `.minecraft/mods` folder
3. Launch Minecraft with the Fabric profile

## Compatibility

- **Minecraft Version**: 1.21.3
- **Fabric API**: Required
- **Other Mods**: Compatible with most Fabric mods
- **Servers**: Works on Crystal PvP servers and similar PvP-focused servers

## Configuration

The mod uses sensible defaults and requires no configuration. All features are enabled by default:

- **Default Mode**: Clipboard mode (safest for most servers)
- **Armor Threshold**: 95% durability warning
- **Render Distance**: 16 blocks for player detection
- **Confirmation Timeout**: 30 seconds for queue confirmation

## Safety Notes

- **Macro Mode**: Use `/gd macro` with caution on servers with strict anti-macro rules
- **Clipboard Mode**: Recommended for most servers as it doesn't send commands automatically
- **Server Rules**: Always check your server's rules regarding mods and automation

## Troubleshooting

### Common Issues
- **Clipboard not working**: Try using `/gd macro` instead
- **False positives**: The mod includes safeguards against legitimate totem pops
- **No lightning effects**: Ensure you're on a server that allows kit detection

### Debug Mode
Enable debug logging by setting `DEBUG_MODE = true` in the mod code for detailed logging.

## Development

This mod is built with:
- **Fabric API**: Minecraft modding framework
- **Gradle**: Build system
- **Mixin**: For modifying Minecraft classes

## License

This project is licensed under the MIT License - see the LICENSE file for details.
