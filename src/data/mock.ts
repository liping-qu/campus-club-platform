export type Club = {
  id: string
  name: string
  category: string
  description: string
  members: number
  color: string
  owner: string
  contact: string
  tags: string[]
  honors: string[]
}

export type Activity = {
  id: string
  clubId: string
  title: string
  location: string
  date: string
  time: string
  joined: number
  capacity: number
  tag: string
  description: string
  rules: string[]
  deadline: string
}

export type Notification = {
  id: string
  title: string
  content: string
  type: string
  read: boolean
  time: string
}

export const clubs: Club[] = [
  { id: '1', name: '青年志愿者协会', category: '公益志愿', description: '用行动服务校园与社区，让每一次志愿都更有意义。', members: 328, color: '#e8f1ff', owner: '林清雅', contact: 'volunteer@campus.edu', tags: ['公益', '志愿服务', '成长'], honors: ['年度优秀社团', '校园公益先锋'] },
  { id: '2', name: '摄影协会', category: '兴趣艺术', description: '记录校园四季，分享拍摄技巧，发现身边的美。', members: 186, color: '#fff2df', owner: '周亦然', contact: 'photo@campus.edu', tags: ['摄影', '创作', '展览'], honors: ['校园影像大赛金奖', '年度人气社团'] },
  { id: '3', name: '街舞社', category: '文体活动', description: '热爱舞蹈，释放活力，欢迎每一位新成员。', members: 142, color: '#f3eaff', owner: '陈子昂', contact: 'dance@campus.edu', tags: ['街舞', '舞台', '活力'], honors: ['校园文化节冠军', '最佳表演团队'] },
  { id: '4', name: '创业实践社', category: '创新创业', description: '连接想法与实践，和志同道合的伙伴一起成长。', members: 96, color: '#e5f8ef', owner: '许安', contact: 'startup@campus.edu', tags: ['创新', '实践', '创业'], honors: ['大学生创新项目金奖', '优秀实践社团'] }
]

export const activities: Activity[] = [
  { id: '1', clubId: '1', title: '校园环保志愿行动', location: '大学生活动中心', date: '10月12日', time: '09:00-11:30', joined: 48, capacity: 60, tag: '报名中', description: '一起清理校园绿地、分类可回收物，用两小时让校园更清爽。', rules: ['请提前 10 分钟到集合点签到', '自备饮水和防晒用品', '服从现场志愿者组长安排'], deadline: '10月11日 18:00' },
  { id: '2', clubId: '2', title: '秋日校园摄影采风', location: '南校区林荫道', date: '10月13日', time: '14:00-17:00', joined: 26, capacity: 30, tag: '即将满员', description: '带上相机或手机，和摄影协会一起捕捉校园里的秋日光影。', rules: ['自备拍摄设备', '集合后统一出发', '优秀作品可参加社团展览'], deadline: '10月12日 20:00' },
  { id: '3', clubId: '3', title: '街舞社公开体验课', location: '体育馆二楼舞蹈室', date: '10月15日', time: '18:30-20:00', joined: 35, capacity: 80, tag: '报名中', description: '零基础也能参加的街舞公开课，跟着社团老师一起释放活力。', rules: ['穿着舒适运动服', '提前热身，量力而行', '课程结束后统一合影'], deadline: '10月15日 16:00' }
]

export const notifications: Notification[] = [
  { id: 'n1', title: '报名成功', content: '你已成功报名「校园环保志愿行动」，记得准时到场。', type: '报名', read: false, time: '今天 09:30' },
  { id: 'n2', title: '活动提醒', content: '「秋日校园摄影采风」即将开始，别忘了带上拍摄设备。', type: '提醒', read: false, time: '昨天 18:20' },
  { id: 'n3', title: '校园公告', content: '校园社团通已上线，欢迎发现更多志同道合的伙伴。', type: '公告', read: true, time: '10月08日' }
]
