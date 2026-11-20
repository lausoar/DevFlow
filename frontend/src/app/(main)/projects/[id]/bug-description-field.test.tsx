import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fileApi } from '@/lib/api/file';
import { BugDescriptionField } from './bug-description-field';

vi.mock('@/lib/api/file', () => ({
  fileApi: { upload: vi.fn() },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function ControlledField() {
  const [value, setValue] = useState('初始文本');
  return (
    <BugDescriptionField projectId="project-1" value={value} onChange={setValue} />
  );
}

describe('BugDescriptionField asynchronous uploads', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('keeps intervening typing and both image links across sequential deferred uploads', async () => {
    const user = userEvent.setup();
    const first = deferred<{ data: { url: string } }>();
    const second = deferred<{ data: { url: string } }>();
    vi.mocked(fileApi.upload)
      .mockReturnValueOnce(first.promise as never)
      .mockReturnValueOnce(second.promise as never);

    const { container } = render(<ControlledField />);
    const textarea = screen.getByRole('textbox');
    const fileInput = container.querySelector('input[type="file"]')!;
    const files = [
      new File(['first'], 'first.png', { type: 'image/png' }),
      new File(['second'], 'second.png', { type: 'image/png' }),
    ];
    fireEvent.change(fileInput, { target: { files } });

    await waitFor(() => expect(fileApi.upload).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('button', { name: '上传中...' })).toBeDisabled();
    await user.type(textarea, ' typed');

    await act(async () => {
      first.resolve({ data: { url: '/files/first.png' } });
      await first.promise;
    });
    await waitFor(() => expect(fileApi.upload).toHaveBeenCalledTimes(2));
    expect(textarea).toHaveValue('初始文本 typed\n![截图](/files/first.png)\n');

    await user.type(textarea, ' between');
    await act(async () => {
      second.resolve({ data: { url: '/files/second.png' } });
      await second.promise;
    });

    await waitFor(() => {
      expect(textarea).toHaveValue(
        '初始文本 typed\n![截图](/files/first.png)\n between\n![截图](/files/second.png)\n',
      );
    });
    expect(screen.getByRole('button', { name: '插入截图' })).toBeEnabled();
  });

  it('retains the existing upload error and controlled text on failure', async () => {
    vi.mocked(fileApi.upload).mockRejectedValueOnce(new Error('network'));
    const { container } = render(<ControlledField />);
    const fileInput = container.querySelector('input[type="file"]')!;
    fireEvent.change(fileInput, {
      target: { files: [new File(['image'], 'failure.png', { type: 'image/png' })] },
    });

    expect(await screen.findByText('图片上传失败，请重试')).toBeInTheDocument();
    expect(screen.getByRole('textbox')).toHaveValue('初始文本');
  });
});
