type UserLike = {
  username?: string | null;
  displayName?: string | null;
};

export const usernameTag = (username?: string | null) => {
  const normalizedUsername = username?.trim();
  if (!normalizedUsername) {
    return '@用户';
  }

  return normalizedUsername.startsWith('@') ? normalizedUsername : `@${normalizedUsername}`;
};

export const displayUserLabel = (user?: UserLike | null) => {
  const displayName = user?.displayName?.trim();
  return displayName || usernameTag(user?.username);
};

export const compactUserLabel = (user?: UserLike | null) => {
  const displayName = user?.displayName?.trim();
  const username = user?.username?.trim();
  return displayName || username || '用户';
};
