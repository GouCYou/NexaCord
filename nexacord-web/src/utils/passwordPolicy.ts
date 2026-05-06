export const passwordPolicyText = '密码需要 8-20 位，并包含大写字母、小写字母、数字和符号。';

export const getPasswordStrengthError = (password: string) => {
  if (!password) {
    return '';
  }

  if (password.length < 8 || password.length > 20) {
    return '密码长度需要在 8 到 20 个字符之间。';
  }

  if (!/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/\d/.test(password) || !/[^A-Za-z0-9]/.test(password)) {
    return passwordPolicyText;
  }

  return '';
};

export const isStrongPassword = (password: string) => !getPasswordStrengthError(password);
