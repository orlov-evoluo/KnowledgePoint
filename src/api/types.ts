/** База личного кабинета «Точка Знаний». Хост кабинета — lk.tochka-school.ru, API — app. */
export const TOCHKA_LK_API = "https://app.tochka-school.ru/api/v1/lk";

/** Сегмент пути журнала. В клиенте кабинета `allFinished` уходит как `allfinished`. */
export type JournalStatus =
  | "active"
  | "allfinished"
  | "finished"
  | "archived"
  | "cancelled";

export type CourseType = "record" | "online";

export type CourseStatus =
  | "active"
  | "finished"
  | "archived"
  | "cancelled"
  | "unknown";

export type CourseTag =
  | "personal"
  | "trial"
  | "debtor"
  | "group"
  | "college"
  | "unknown";

export type LessonType = "record" | "online" | "text";

export type LessonStatus =
  | "accepted"
  | "rejected"
  | "fix_required"
  | "unknown";

export type StatsItemType =
  | "lesson"
  | "homework"
  | "test"
  | "fixRequired"
  | "unknown";

export interface CountPair {
  completed: number;
  total: number;
}

export interface HomeworkStats {
  accepted: number;
  fixRequired: number;
  total: number;
}

export interface PersonRef {
  id: number;
  firstName: string;
  lastName: string;
  avatar?: string | null;
}

export interface JournalStudent {
  id: number;
  parentId: number | null;
  firstName: string;
  lastName: string;
  middleName: string | null;
  username: string;
  email: string | null;
}

export interface JournalCourse {
  id: number;
  courseName: string;
  courseType: CourseType;
  courseStatus: CourseStatus;
  needTutor: boolean;
  tags: CourseTag[];
  watches: CountPair;
  homeworks: HomeworkStats;
  /** Есть в `stats.courses`, в кратком списке `courses` обычно нет. */
  test?: CountPair | null;
  teacher?: PersonRef | null;
  tutor?: PersonRef | null;
}

export interface StatsItem {
  type: StatsItemType;
  /** В ответе приходит строкой, например «12 из 20». */
  total: string;
  title: string;
  subTitle: string | null;
}

export interface Journal {
  student: JournalStudent;
  general: {
    watches: CountPair;
    webinars: CountPair;
    homeworks: HomeworkStats;
  } | null;
  courses: JournalCourse[];
  stats: {
    general: StatsItem[];
    courses: JournalCourse[];
  } | null;
}

export interface LessonTest {
  total: number;
  completed: number;
  successRation: number | null;
  score: number | null;
  isSuccess: boolean;
}

export interface JournalLesson {
  id: number;
  name: string;
  type: LessonType;
  record: boolean;
  webinar: boolean;
  status: LessonStatus;
  homework: boolean;
  watchProgress: number | null;
  test: LessonTest | null;
  chatId: number | null;
  testKey: string | null;
  messageId: number | null;
  isAvailable: boolean;
  isTextLessonWatched: boolean;
  isWatched: boolean;
}

export interface JournalCourseDetail {
  student: JournalStudent;
  course: JournalCourse & {
    generalStats: StatsItem[];
  };
  lessons: JournalLesson[];
}

export interface Profile {
  id: number;
  parentId: number | null;
  avatar: string | null;
  firstName: string;
  lastName: string;
  middleName: string | null;
  username: string;
  gender: "woman" | "man" | null;
  email: string | null;
  phone: string | null;
  birthday: string | null;
  city: string | null;
  timeZone: string | null;
  isConfirmPhone: boolean;
  isConfirmEmail: boolean;
  isSubscribeNews: boolean;
  isDeleted: boolean;
  onlineAt: string | null;
  createdAt: string;
}

export interface ProfileResponse {
  client: Profile;
}

export interface ChildrenResponse {
  children: Profile[];
}

export interface DateRange {
  /** `yyyy-MM-dd` */
  startDate?: string;
  /** `yyyy-MM-dd` */
  endDate?: string;
}
