import api from './api';

const MAX_IMAGE_EDGE = 1920;
const COMPRESSION_QUALITY = 0.82;
const SKIP_COMPRESSION_TYPES = new Set(['image/gif', 'image/svg+xml']);

class FileService {
  async uploadFile(file: File): Promise<string> {
    if (!this.isImageFile(file)) {
      throw new Error('当前只能上传图片文件。');
    }

    const uploadableFile = await this.compressImage(file).catch(() => file);
    return api.uploadFile<string>('/files/upload', uploadableFile);
  }

  async deleteFile(fileUrl: string): Promise<void> {
    return api.delete('/files/delete', {
      params: {
        fileUrl,
      },
    });
  }

  async getPresignedUrl(fileUrl: string, expirationMinutes = 15): Promise<string> {
    return api.get<string>('/files/presigned-url', {
      params: {
        fileUrl,
        expirationMinutes,
      },
    });
  }

  isImageFile(file: File): boolean {
    return file.type.startsWith('image/');
  }

  private async compressImage(file: File): Promise<File> {
    if (
      SKIP_COMPRESSION_TYPES.has(file.type) ||
      !file.type.startsWith('image/') ||
      typeof createImageBitmap !== 'function'
    ) {
      return file;
    }

    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_IMAGE_EDGE / Math.max(bitmap.width, bitmap.height));
    if (scale >= 1 && file.size <= 900 * 1024) {
      bitmap.close?.();
      return file;
    }

    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext('2d');
    if (!context) {
      bitmap.close?.();
      return file;
    }

    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close?.();

    const outputType = file.type === 'image/png' ? 'image/webp' : file.type;
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, outputType, COMPRESSION_QUALITY);
    });

    if (!blob || blob.size >= file.size) {
      return file;
    }

    const extension = outputType.split('/')[1] || 'jpg';
    const fileName = file.name.replace(/\.[^.]+$/, `.${extension}`);
    return new File([blob], fileName, {
      type: outputType,
      lastModified: Date.now(),
    });
  }
}

export default new FileService();
