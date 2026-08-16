# Mink’s Day sprite source

`mink_states_chroma.png` is the editable 3 × 2 source sheet used to produce the transparent Android runtime asset at `app/src/main/res/drawable-nodpi/mink_states.png`.

Each cell is 512 × 512 pixels. Reading left to right, top to bottom:

1. Calm walk
2. Purposeful walk
3. Looking at phone
4. Distracted / looking around
5. Sitting / resting
6. Sleeping beside the burrow

The source was generated in one pass against a flat `#00ff00` chroma background, then converted to transparency with a soft matte and green despill. The runtime sheet remains a single lossless RGBA PNG so every state is decoded once and cropped by `MinkSprite` in Compose.

Prompt direction: use the existing MinkLauncher Open face as the canonical character; make a clean Android UI sprite sheet with consistent cream fur, dark brown linework, proportions, scale, lighting, and no text or watermark.
