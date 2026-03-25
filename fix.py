import sys
file_path = '/Users/tafadzwachinombe/Documents/projectboost/VolumeStream/data/src/commonMain/kotlin/com.vditital/data/datasource/SessionDataSourceImpl.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()
with open(file_path, 'w') as f:
    for line in lines:
        if 'val ok = response.status' in line:
            f.write('        val ok = response.status == HttpStatusCode.Created \x7c\x7c response.status == HttpStatusCode.OK \x7c\x7c response.status.value == 409\n')
        else:
            f.write(line)
