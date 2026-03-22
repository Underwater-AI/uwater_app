with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text = f.read()

old_list = 'val nonMarineClasses = listOf("suit", "sunglasses", "neck brace", "face", "person", "car", "dog", "cat", "mask", "oxygen mask")'
new_list = 'val nonMarineClasses = listOf("suit", "sunglasses", "neck brace", "face", "person", "car", "dog", "cat", "mask", "oxygen mask", "bikini", "racket", "building", "tree", "street", "bicycle", "laptop", "phone", "furniture", "clothing")'

old_marine = 'val marineClasses = listOf("fish", "scuba diver", "coral reef", "anemone", "shark", "whale", "ray", "submarine", "turtle", "crab", "jellyfish", "sea")'
new_marine = 'val marineClasses = listOf("fish", "scuba diver", "coral reef", "anemone", "shark", "whale", "ray", "submarine", "turtle", "crab", "jellyfish", "sea", "water", "ocean", "diver", "reef", "marine", "kelp")'

text = text.replace(old_list, new_list).replace(old_marine, new_marine)

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text)
