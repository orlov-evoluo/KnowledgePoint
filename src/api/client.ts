import {
  TOCHKA_LK_API,
  type ChildrenResponse,
  type DateRange,
  type Journal,
  type JournalCourseDetail,
  type JournalStatus,
  type ProfileResponse,
} from "./types";

export class TochkaApiError extends Error {
  constructor(
    readonly status: number,
    readonly path: string,
    readonly body: string,
  ) {
    super(`Tochka API ${status} ${path}`);
    this.name = "TochkaApiError";
  }
}

export interface TochkaClientOptions {
  /** JWT целиком или уже с префиксом `Bearer `. */
  token: string;
  /** Подмена fetch: на лендинге с чужого домена нужен серверный прокси. */
  fetch?: typeof fetch;
  baseUrl?: string;
}

/**
 * Слой кабинета, которого хватает для лендинга успеваемости.
 *
 * Браузер кабинета ходит на `https://app.tochka-school.ru` с origin
 * `https://lk.tochka-school.ru`. С другого домена запрос из браузера
 * API не отдаст — вызывайте клиент с сервера и отдавайте на лендинг уже свои данные.
 */
export class TochkaClient {
  private readonly token: string;
  private readonly fetchImpl: typeof fetch;
  private readonly baseUrl: string;

  constructor(options: TochkaClientOptions) {
    const raw = options.token.trim();
    this.token = raw.toLowerCase().startsWith("bearer ") ? raw : `Bearer ${raw}`;
    this.fetchImpl = options.fetch ?? fetch;
    this.baseUrl = (options.baseUrl ?? TOCHKA_LK_API).replace(/\/$/, "");
  }

  profile(): Promise<ProfileResponse> {
    return this.get("/profile");
  }

  children(): Promise<ChildrenResponse> {
    return this.get("/children");
  }

  /**
   * Сводка успеваемости.
   * `studentId` — `clientId` из JWT, для этого кабинета 79856.
   * Без дат API отдаёт весь выбранный статус.
   */
  journal(
    studentId: number,
    status: JournalStatus = "active",
    range?: DateRange,
  ): Promise<Journal> {
    return this.get(`/journal/${studentId}/${status}`, range);
  }

  /** Уроки одного курса со страницы `/learning-performance/:studentId/course/:clientCourseId`. */
  journalCourse(
    studentId: number,
    courseId: number,
    range?: DateRange,
  ): Promise<JournalCourseDetail> {
    return this.get(`/journal/${studentId}/detail/${courseId}`, range);
  }

  private async get<T>(path: string, range?: DateRange): Promise<T> {
    const url = new URL(this.baseUrl + path);
    if (range?.startDate) url.searchParams.set("startDate", range.startDate);
    if (range?.endDate) url.searchParams.set("endDate", range.endDate);

    const response = await this.fetchImpl(url, {
      method: "GET",
      headers: {
        Authorization: this.token,
        Accept: "application/json",
      },
    });

    const body = await response.text();
    if (!response.ok) {
      throw new TochkaApiError(response.status, path, body);
    }
    return JSON.parse(body) as T;
  }
}
