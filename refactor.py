import os

def replace_in_file(filepath, old_str, new_str):
    with open(filepath, 'r', encoding='utf-8') as file:
        content = file.read()
    
    if old_str in content:
        content = content.replace(old_str, new_str)
        with open(filepath, 'w', encoding='utf-8') as file:
            file.write(content)
        print(f"Updated: {filepath}")

def process_directory(directory, old_str, new_str):
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(('.kt', '.xml', '.kts')):
                filepath = os.path.join(root, file)
                replace_in_file(filepath, old_str, new_str)

if __name__ == "__main__":
    old_pkg = "com.smartsleep.alarm"
    new_pkg = "com.x13labs.dreampulse"
    
    project_dir = r"c:\WearOS\app"
    process_directory(project_dir, old_pkg, new_pkg)
    
    # Update build.gradle.kts (it's at the root of app)
    replace_in_file(os.path.join(project_dir, "build.gradle.kts"), old_pkg, new_pkg)
