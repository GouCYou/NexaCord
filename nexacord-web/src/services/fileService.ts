import api from './api';

class FileService {
  async uploadFile(file: File): Promise<string> {
    return api.uploadFile<string>('/files/upload', file);
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
}

export default new FileService();
