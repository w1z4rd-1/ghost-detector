# insignia
 A Minecraft Fabric mod that categorizes other players into custom groups and changes their display names in the tab-menu and above the players' heads to their actual account name with a colour of your choice.

## Commands

### Tagging Players
- `/tag <playername> <tag> [color]` - Assigns a tag with optional color
  - Tab completion for tags: Runner, Competent, Skilled, Creature
  - Available colors: black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white
  - Colors are stored by name (e.g., "blue" instead of "§9")
  - Default colors:
    - Skilled: Cyan (aqua)
    - Competent: Purple (dark_purple)
    - Runner/Creature: Red
    - Others: Gold
  - Players tagged as "T" don't show the tag in tab/nametag
  - Players with private stats are auto-tagged as "private" in blue
- `/tag remove <playername>` - Removes the tag from a player
- `/tag help` - Shows the help message

### Stats Checking
- `/sc <playername>` - Reads a player's stats by running the /stats command and extracting information from the GUI
  - Displays Crystal 1v1 stats with color-coded formatting
  - Shows wins, win percentage, current win streak, and best win streak
  - Colors indicate the player's skill level
  - Automatically stores these stats in the player's data
  - Updates last checked timestamp
  - Handles private statistics
  - Empty stats (all zeros) are not stored
  - Automatically tags player as "T" (gold color) if they don't already have a tag
  - Waits 100ms after the stats GUI appears before reading data to ensure all data is loaded

### Auto Stats Checking
- `/autosc` - Automatically checks stats for all untagged players in the tab list
  - Processes players sequentially with a 2-second delay between each
  - Shows progress updates in chat
  - Automatically tags players as "T" (gold color) when their stats are fetched
  - Skips players who already have tags
  - Only processes players currently visible in the tab list

### Automatic Queue Stats Display
- Immediately shows cached stats when a player joins the queue
- Detects queue joins through chat messages
- Shows Crystal 1v1 stats with the same formatting as the `/sc` command
- Includes the player's tag and color if they've been tagged
- Shows "X days ago" for stats older than 2 weeks
- Only uses cached stats from tags.json, never runs /stats automatically

## Data Storage

The mod stores player data with the following information:
- UUID
- Tag
- Color (stored as a readable name, e.g., "red", "blue")
- Crystal 1v1 stats (only stored if player has non-zero stats)
- hasStats flag
- Date stats were last checked
- Private stats flag
