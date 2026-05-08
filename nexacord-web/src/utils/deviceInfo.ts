const browserMatchers: Array<[RegExp, string]> = [
  [/Edg\//, 'Edge'],
  [/Chrome\//, 'Chrome'],
  [/Firefox\//, 'Firefox'],
  [/Safari\//, 'Safari'],
];

const platformMatchers: Array<[RegExp, string]> = [
  [/Mac OS X|Macintosh/, 'macOS'],
  [/Windows NT/, 'Windows'],
  [/Android/, 'Android'],
  [/iPhone|iPad|iPod/, 'iOS'],
  [/Linux/, 'Linux'],
];

export const getDeviceName = (): string => {
  if (typeof navigator === 'undefined') {
    return '未知设备';
  }

  const userAgent = navigator.userAgent || '';
  const browser = browserMatchers.find(([pattern]) => pattern.test(userAgent))?.[1] || '浏览器';
  const platform = platformMatchers.find(([pattern]) => pattern.test(userAgent))?.[1] || navigator.platform || '当前设备';

  return `${browser} on ${platform}`;
};
