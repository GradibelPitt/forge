# DIY 简中翻译桥接

只编辑 `custom/translations/cardnames-zh-CN-custom.txt`，可以直接在 GitHub 网页编辑这个小文件。

现有 `forge-gui/res/languages/cardnames-zh-CN.txt`（包括里面已有的 DIY 卡）全部保留，不迁移、不删除。客户端启动时先读大文件，再读同目录的 `cardnames-zh-CN-custom.txt`：不同名称追加，相同名称覆盖。小文件初始没有卡牌记录，所以本次不会改变任何现有卡牌的中文。

## 添加或修改

每张卡一行，使用卡牌脚本的精确内部名称，不要用英文别名代替中文内部名称：

```text
内部名称|中文显示名|中文类别|中文Oracle
```

- 新卡：直接往小文件添加完整四字段记录。
- 旧卡改中文：在小文件添加同名完整记录，大文件中的旧记录保留。
- 撤销覆盖：删掉小文件中的对应行，再同步、发布并重启；客户端回退到大文件。
- Oracle 换行写成字面量 `\n`；规则中的竖线写成 `VERT`。解析仍使用 Forge 原有规则（包括 Class 和 functional variant 处理）。
- 同一小文件内不允许重复 key；`Name$C` 与 `Name $C` 被视为同一变体 key。
- 以 `#` 开头的行为注释。不需要预先把任何旧卡搬过来。
- 沿用原 parser 的兼容行为：省略或留空末尾 Oracle 字段不会清除大文件的旧 Oracle；覆盖旧 Oracle 时请提供完整非空规则文字。

## 安装与发布

开发运行时可仅同步翻译（不接触用户 profile）：

```powershell
custom/tools/install_to_forge.ps1 -TranslationsOnly
```

这会校验格式和重复 key，再按字节复制到 `forge-gui/res/languages/cardnames-zh-CN-custom.txt`。该副本是生成文件，不应手工修改或提交。

运行仓库的 `tools/publish_git_payload.ps1 -SyncCustom` 或 `-SyncLocalization` 会从这个权威小文件复制到 `app/res/languages/cardnames-zh-CN-custom.txt`，校验 SHA-256 并纳入关键资源清单。常规发布命令的 `ForgeRoot`、`BuildId` 参数照旧；第一次启用桥接需要同时发布新的 `forge-core` 模块，后续只改翻译不需要重新构建 Java。

修改翻译后需正常重启 Forge。老运行包没有新 `forge-core` 时不会读取小文件。

如果只发布这个小文件，使用 `publish_git_payload.ps1 -SyncCustomTranslations`（同样提供 `ForgeRoot`、`BuildId`）。这个精准开关保留运行包现有大文件、卡牌脚本及图片，适用于运行仓库另有较新内容的情况；首次桥接发布同时加 `-Module forge-core`。
