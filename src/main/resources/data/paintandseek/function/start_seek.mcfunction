# Runs once when the seek phase begins (server context). Edit freely.
effect clear @a[tag=paintandseek.seeker] minecraft:blindness
title @a times 10 70 20
title @a[tag=paintandseek.seeker] title {"text":"SEEK!","bold":true,"color":"gold"}
title @a[tag=paintandseek.hider] title {"text":"HIDE!","bold":true,"color":"green"}
