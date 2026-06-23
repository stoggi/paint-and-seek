# Runs at the start of a new round to clear leftovers (server context). Edit freely.
# Clears ALL effects (leftover winner-glow, blindness, etc.) before roles are
# assigned - so it lands before setup_seeker blinds the new seeker.
effect clear @a
# Keep items on death for the whole round (MC 26.2 gamerule id is snake_case).
gamerule keep_inventory true
