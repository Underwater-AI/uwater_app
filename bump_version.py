import re

with open("app/build.gradle.kts", "r") as f:
    text = f.read()

text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 8', text)
text = re.sub(r'versionName\s*=\s*"\d+\.\d+\.\d+"', 'versionName = "1.0.7"', text)

with open("app/build.gradle.kts", "w") as f:
    f.write(text)
