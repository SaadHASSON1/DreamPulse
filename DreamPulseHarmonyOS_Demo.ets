// DreamPulse - Huawei (HarmonyOS) Demo
// هذه الواجهة الرئيسية باستخدام إطار عمل ArkUI ولغة ArkTS

@Entry
@Component
struct MainScreen {
  @State isTracking: boolean = false;
  @State heartRate: number = 0;

  build() {
    Column() {
      Text('DreamPulse')
        .fontSize(24)
        .fontColor(Color.Blue)
        .fontWeight(FontWeight.Bold)
        .margin({ top: 20 })

      Blank()

      if (this.isTracking) {
        // أيقونة و نص عند تفعيل التتبع
        Text('🌙')
          .fontSize(40)
        Text('جاري تتبع النوم...')
          .fontSize(18)
          .fontColor(Color.Green)
          .margin({ top: 10 })
        
        Text(`النبض: ${this.heartRate} BPM`)
          .fontSize(14)
          .fontColor(Color.Gray)
          .margin({ top: 5 })
      } else {
        // أيقونة ونص في حالة عدم التتبع
        Text('🛏️')
          .fontSize(40)
        Text('جاهز للنوم؟')
          .fontSize(18)
          .margin({ top: 10 })
      }

      Blank()

      // زر التشغيل والإيقاف
      Button(this.isTracking ? 'إيقاف التتبع' : 'بدء التتبع')
        .onClick(() => {
          this.toggleTracking();
        })
        .backgroundColor(this.isTracking ? Color.Red : Color.Blue)
        .width('80%')
        .height(45)
        .margin({ bottom: 20 })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.SpaceBetween)
  }

  // دالة لتشغيل وإيقاف التتبع
  toggleTracking() {
    this.isTracking = !this.isTracking;
    if (this.isTracking) {
      console.info('DreamPulse: بدء قراءة الحساسات من Huawei Health Kit...');
      // هنا سنكتب كود تشغيل الحساسات والمنبه
    } else {
      console.info('DreamPulse: إيقاف التتبع...');
    }
  }
}
