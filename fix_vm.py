with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text = f.read()

import re
text = text.replace('                    warning = "⚠️ **WARNING: Ecosystem Mismatch**\\nThis image does not strongly match trained underwater environments. Analysis accuracy is reduced.\\n"', '                    warning = "⚠️ **WARNING: Ecosystem Mismatch**\\nThis image does not strongly match trained underwater environments. Analysis accuracy is reduced.\\n\\n"')

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text)
