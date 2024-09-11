# uu-message-convert

## 起因
各平台资源码不统一, 导致切换平台, 资源码容易不兼容

## 解决
在业务层实现统一的. 标准的资源码, 不使用平台的资源码. 如 CQ 码...

## 案例
由 <> 包裹

### 图片码
`<image file="http://demo-image.com">`
`<image file="file:///c:\\demo.png">`
  
#### 拓展  
使用前提需要指定当前目录

`<image file="./demo.png">`

#### 别名
使用前提需要指定工作目录
`<image file="@/demo.png">`

### 图文
`hi <image file="http://demo-image.com">`

## 数组
