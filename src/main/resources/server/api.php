<?php
/**
 * RenEspresso - Image & Category Management API
 * Handles client-to-server image uploads, edits, deletions, and category folder management.
 */

header('Content-Type: application/json; charset=UTF-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$baseDir = realpath(__DIR__);
if (!$baseDir) {
    $baseDir = __DIR__;
}

$action = $_REQUEST['action'] ?? 'ping';

// Helper: send JSON response
function jsonOut($success, $data = [], $message = '', $statusCode = 200) {
    http_response_code($statusCode);
    echo json_encode(array_merge([
        'success' => $success,
        'message' => $message
    ], $data), JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit;
}

// Helper: sanitize and find or create category folder
function resolveCategoryFolder($baseDir, $categoryName, $autoCreate = true) {
    if (empty($categoryName)) {
        $folder = 'General';
    } else {
        $clean = trim($categoryName);
        $normalizedTarget = preg_replace('/[^a-zA-Z0-9]/', '', strtolower($clean));

        $found = null;
        if ($handle = opendir($baseDir)) {
            while (false !== ($entry = readdir($handle))) {
                if ($entry !== '.' && $entry !== '..' && is_dir($baseDir . '/' . $entry)) {
                    $norm = preg_replace('/[^a-zA-Z0-9]/', '', strtolower($entry));
                    if ($norm === $normalizedTarget) {
                        $found = $entry;
                        break;
                    }
                }
            }
            closedir($handle);
        }

        if ($found) {
            $folder = $found;
        } else {
            // Generate clean PascalCase folder name e.g. "Matcha Series" -> "MatchaSeries"
            $words = preg_split('/[\s_-]+/', preg_replace('/[^a-zA-Z0-9_& -]/', '', $clean));
            $pascal = '';
            foreach ($words as $w) {
                if (!empty($w)) $pascal .= ucfirst($w);
            }
            $folder = !empty($pascal) ? $pascal : 'Category_' . time();
        }
    }

    $targetDir = $baseDir . '/' . $folder;
    if ($autoCreate && !is_dir($targetDir)) {
        @mkdir($targetDir, 0777, true);
    }
    return $folder;
}

// Helper: safely resolve file path within base directory to prevent directory traversal
function safePath($baseDir, $relativePath) {
    if (empty($relativePath)) return false;
    $cleanRelative = str_replace(['\\', '..'], ['/', ''], $relativePath);
    $cleanRelative = ltrim($cleanRelative, '/');
    $fullPath = $baseDir . '/' . $cleanRelative;
    return $fullPath;
}

switch ($action) {

    // -------------------------------------------------------------
    // PING / STATUS
    // -------------------------------------------------------------
    case 'ping':
        jsonOut(true, [
            'status' => 'online',
            'baseDir' => $baseDir,
            'timestamp' => date('Y-m-d H:i:s')
        ], 'RenEspresso Image API is active and ready.');
        break;

    // -------------------------------------------------------------
    // UPLOAD / EDIT IMAGE
    // -------------------------------------------------------------
    case 'upload':
    case 'edit':
    case 'save_image':
        if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
            jsonOut(false, [], 'POST method required for upload.', 405);
        }

        $uploadedFile = $_FILES['file'] ?? $_FILES['image'] ?? null;
        if (!$uploadedFile || $uploadedFile['error'] !== UPLOAD_ERR_OK) {
            $errCode = $uploadedFile['error'] ?? 'No file received';
            jsonOut(false, ['error_code' => $errCode], 'No valid image file uploaded.', 400);
        }

        $categoryName = $_POST['category'] ?? '';
        $customName = $_POST['custom_name'] ?? $_POST['dish_name'] ?? '';
        $oldPath = $_POST['old_path'] ?? '';

        $folder = resolveCategoryFolder($baseDir, $categoryName, true);
        $targetDir = $baseDir . '/' . $folder;

        // Determine extension
        $origName = $uploadedFile['name'];
        $ext = strtolower(pathinfo($origName, PATHINFO_EXTENSION));
        if (!in_array($ext, ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'])) {
            $ext = 'png';
        }

        // Generate target filename
        if (!empty($customName)) {
            $cleanDish = preg_replace('/[^a-zA-Z0-9_-]/', '', str_replace(' ', '', ucwords($customName)));
            $fileName = (!empty($cleanDish) ? $cleanDish : 'dish_' . time()) . '.' . $ext;
        } else {
            $cleanBase = preg_replace('/[^a-zA-Z0-9_-]/', '', pathinfo($origName, PATHINFO_FILENAME));
            $fileName = (!empty($cleanBase) ? $cleanBase : 'img_' . time()) . '.' . $ext;
        }

        $destFullPath = $targetDir . '/' . $fileName;

        // Move uploaded file
        if (!move_uploaded_file($uploadedFile['tmp_name'], $destFullPath)) {
            jsonOut(false, [], 'Failed to move uploaded file to ' . $destFullPath, 500);
        }

        $savedRelativePath = $folder . '/' . $fileName;

        // If editing and old path exists, delete old image if it's different
        if (!empty($oldPath) && $oldPath !== $savedRelativePath) {
            $oldFull = safePath($baseDir, $oldPath);
            if ($oldFull && file_exists($oldFull) && is_file($oldFull)) {
                @unlink($oldFull);
            }
        }

        jsonOut(true, [
            'path' => $savedRelativePath,
            'folder' => $folder,
            'filename' => $fileName,
            'size' => filesize($destFullPath)
        ], 'Image uploaded successfully.');
        break;

    // -------------------------------------------------------------
    // DELETE IMAGE
    // -------------------------------------------------------------
    case 'delete':
    case 'delete_image':
        $path = $_REQUEST['path'] ?? $_REQUEST['image_path'] ?? '';
        if (empty($path)) {
            jsonOut(false, [], 'Image path is required.', 400);
        }

        $fullPath = safePath($baseDir, $path);
        if (!$fullPath || !file_exists($fullPath) || !is_file($fullPath)) {
            // Already doesn't exist, treat as success
            jsonOut(true, ['path' => $path], 'File not found or already deleted.');
        }

        if (@unlink($fullPath)) {
            jsonOut(true, ['path' => $path], 'Image deleted successfully.');
        } else {
            jsonOut(false, ['path' => $path], 'Could not delete image file.', 500);
        }
        break;

    // -------------------------------------------------------------
    // CREATE CATEGORY FOLDER
    // -------------------------------------------------------------
    case 'create_category_folder':
        $categoryName = $_REQUEST['category'] ?? '';
        if (empty($categoryName)) {
            jsonOut(false, [], 'Category name is required.', 400);
        }

        $folder = resolveCategoryFolder($baseDir, $categoryName, true);
        $folderPath = $baseDir . '/' . $folder;

        if (is_dir($folderPath)) {
            jsonOut(true, [
                'folder' => $folder,
                'path' => $folderPath
            ], 'Category folder ready.');
        } else {
            jsonOut(false, [], 'Could not create category folder: ' . $folder, 500);
        }
        break;

    // -------------------------------------------------------------
    // DELETE CATEGORY FOLDER
    // -------------------------------------------------------------
    case 'delete_category_folder':
        $categoryName = $_REQUEST['category'] ?? '';
        if (empty($categoryName)) {
            jsonOut(false, [], 'Category name is required.', 400);
        }

        $folder = resolveCategoryFolder($baseDir, $categoryName, false);
        $folderPath = $baseDir . '/' . $folder;

        // Safety verification: must be a subfolder of baseDir and not root or empty
        if (!$folderPath || !is_dir($folderPath) || $folderPath === $baseDir) {
            jsonOut(true, ['folder' => $folder], 'Folder not found or already removed.');
        }

        // Delete all files inside the category folder first
        $files = glob($folderPath . '/*');
        if ($files) {
            foreach ($files as $f) {
                if (is_file($f)) {
                    @unlink($f);
                }
            }
        }

        if (@rmdir($folderPath)) {
            jsonOut(true, ['folder' => $folder], 'Category folder deleted successfully.');
        } else {
            jsonOut(false, ['folder' => $folder], 'Could not remove directory (may not be empty).', 500);
        }
        break;

    default:
        jsonOut(false, [], 'Invalid action: ' . htmlspecialchars($action), 400);
        break;
}
