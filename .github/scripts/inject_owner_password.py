import os
import sys

path = sys.argv[1]
content = open(path).read()
escaped = os.environ["OWNER_PASSWORD"].replace("'", "''")
content = content.replace("__OWNER_PASSWORD_PLACEHOLDER__", escaped)
open(path, "w").write(content)
